package com.cellclaw.upload

import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ResumeUploaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validator = ResumeFileValidator()
    private val uriProvider = FakeResumeUriProvider("content://com.cellclaw.fileprovider/resumes")
    private lateinit var uploader: ResumeUploader

    @Before
    fun setUp() {
        uploader = ResumeUploader(validator, uriProvider)
    }

    private fun createPdfFile(name: String, content: String): Pair<File, String> {
        val file = tempFolder.newFile(name)
        val fullContent = "%PDF-1.4\n$content\n%%EOF"
        val bytes = fullContent.toByteArray(Charsets.UTF_8)
        file.writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sha256 = digest.joinToString("") { "%02x".format(it) }
        return file to sha256
    }

    @Test
    fun testFileDoesNotExistReturnsFileNotFound() {
        val missingFile = File(tempFolder.root, "non_existent.pdf")
        val result = validator.validate(missingFile, expectedSha256 = "dummy_hash")
        assertTrue(result is ValidationResult.FileNotFound)
    }

    @Test
    fun testEmptyFileReturnsEmptyFile() {
        val emptyFile = tempFolder.newFile("empty.pdf")
        val result = validator.validate(emptyFile, expectedSha256 = "dummy_hash")
        assertTrue(result is ValidationResult.EmptyFile)
    }

    @Test
    fun testInvalidMagicBytesReturnsInvalidMagicBytes() {
        val nonPdf = tempFolder.newFile("test.txt")
        nonPdf.writeText("Not a PDF file at all")
        val digest = MessageDigest.getInstance("SHA-256").digest(nonPdf.readBytes())
        val hash = digest.joinToString("") { "%02x".format(it) }

        val result = validator.validate(nonPdf, expectedSha256 = hash)
        assertTrue(result is ValidationResult.InvalidMagicBytes)
    }

    @Test
    fun testHashMismatchReturnsHashMismatch() {
        val (pdfFile, actualHash) = createPdfFile("resume.pdf", "John Doe Profile Facts")
        val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"

        val result = validator.validate(pdfFile, expectedSha256 = wrongHash)
        assertTrue(result is ValidationResult.HashMismatch)
        val mismatch = result as ValidationResult.HashMismatch
        assertEquals(actualHash, mismatch.actualSha256)
        assertEquals(wrongHash, mismatch.expectedSha256)
    }

    @Test
    fun testFileTooLargeReturnsFileTooLarge() {
        val (pdfFile, actualHash) = createPdfFile("large_resume.pdf", "Lots of valid resume content")
        val maxLimit = 10L // Very small limit

        val result = validator.validate(pdfFile, expectedSha256 = actualHash, maxSizeBytes = maxLimit)
        assertTrue(result is ValidationResult.FileTooLarge)
    }

    @Test
    fun testValidPdfMatchesHashAndPasses() {
        val (pdfFile, actualHash) = createPdfFile("valid_resume.pdf", "Verified profile facts")
        val result = validator.validate(pdfFile, expectedSha256 = actualHash)
        assertTrue(result is ValidationResult.Valid)
        val valid = result as ValidationResult.Valid
        assertEquals(actualHash, valid.sha256)
        assertTrue(valid.sizeBytes > 0)
    }

    @Test
    fun testPrepareUploadSuccessGeneratesContentUri() {
        val (pdfFile, actualHash) = createPdfFile("resume_Google_SWE_v1.pdf", "Verified facts")
        val appRecord = ApplicationRecord(
            id = "app-123",
            company = "Google",
            role = "SWE",
            jdHash = "jd123",
            eligibilityResult = "ELIGIBLE",
            resumeVersion = 1,
            resumeHash = actualHash,
            state = ApplicationState.READY_FOR_REVIEW
        )

        val prepResult = uploader.prepareUpload(pdfFile, appRecord)
        assertTrue(prepResult is ResumePreparationResult.Success)
        val success = prepResult as ResumePreparationResult.Success
        assertEquals("content://com.cellclaw.fileprovider/resumes/resume_Google_SWE_v1.pdf", success.contentUri)
        assertEquals(actualHash, success.sha256)
        assertEquals("resume_Google_SWE_v1.pdf", success.fileName)
    }

    @Test
    fun testPrepareUploadFailsIfRecordHasDifferentHash() {
        val (pdfFile, actualHash) = createPdfFile("outdated_resume.pdf", "Old content")
        val appRecord = ApplicationRecord(
            id = "app-124",
            company = "Meta",
            role = "SWE",
            jdHash = "jd124",
            eligibilityResult = "ELIGIBLE",
            resumeVersion = 2,
            resumeHash = "other_hash_v2",
            state = ApplicationState.READY_FOR_REVIEW
        )

        val prepResult = uploader.prepareUpload(pdfFile, appRecord)
        assertTrue(prepResult is ResumePreparationResult.Failure)
        val failure = prepResult as ResumePreparationResult.Failure
        assertTrue(failure.reason.contains("Hash mismatch") || failure.reason.contains("mismatch"))
    }

    @Test
    fun testHandlePickerCancelledReturnsCancelledState() {
        val status = uploader.handlePickerResult(PickerResult.Cancelled)
        assertTrue(status is UploadStatus.Cancelled)
    }

    @Test
    fun testHandlePickerPermissionDeniedReturnsPermissionDenied() {
        val status = uploader.handlePickerResult(PickerResult.PermissionDenied)
        assertTrue(status is UploadStatus.PermissionDenied)
    }

    @Test
    fun testHandleServerResponses() {
        val (pdfFile, actualHash) = createPdfFile("resume.pdf", "Verified")
        uploader.prepareUpload(pdfFile, ApplicationRecord(
            company = "Google",
            role = "SWE",
            jdHash = "jd1",
            eligibilityResult = "ELIGIBLE",
            resumeHash = actualHash
        ))

        // Server reports file too large
        val tooLarge = uploader.handleServerResponse(ServerUploadResponse.FileTooLarge(sizeBytes = 15000000, limitBytes = 10000000))
        assertTrue(tooLarge is UploadStatus.TooLarge)

        // Server rejected
        val rejected = uploader.handleServerResponse(ServerUploadResponse.Rejected("Upload quota exceeded"))
        assertTrue(rejected is UploadStatus.ServerRejected)
        assertEquals("Upload quota exceeded", (rejected as UploadStatus.ServerRejected).reason)

        // Server timeout
        val timeout = uploader.handleServerResponse(ServerUploadResponse.Timeout)
        assertTrue(timeout is UploadStatus.Timeout)

        // Server accepted
        val success = uploader.handleServerResponse(ServerUploadResponse.Accepted("resume.pdf"))
        assertTrue(success is UploadStatus.Confirmed)
        assertEquals("resume.pdf", (success as UploadStatus.Confirmed).fileName)
        assertEquals(actualHash, (success as UploadStatus.Confirmed).sha256)
    }
}
