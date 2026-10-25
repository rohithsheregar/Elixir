package com.cellclaw.approval

import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubmitSupervisorTest {

    private lateinit var tokenManager: ApprovalTokenManager
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var detector: ConfirmationDetector
    private lateinit var supervisor: SubmitSupervisor

    @Before
    fun setUp() = runBlocking {
        tokenManager = ApprovalTokenManager()
        tracker = InMemoryApplicationTracker()
        detector = ConfirmationDetector()
        supervisor = SubmitSupervisor(tokenManager, tracker, detector)
    }

    private suspend fun createTestRecord(appId: String = "app-submit-1"): ApplicationRecord {
        val record = ApplicationRecord(
            id = appId,
            company = "Google",
            role = "SWE",
            jdHash = "jd_test_hash",
            eligibilityResult = "ELIGIBLE",
            resumeHash = "resume_test_hash",
            state = ApplicationState.READY_FOR_REVIEW
        )
        tracker.registerApplication(record)
        return record
    }

    @Test
    fun testConfirmationDetectorRecognizesGoogleFormsReceiptAndReference() {
        val pageTexts = listOf(
            "Google Forms",
            "Your response has been recorded.",
            "Reference: CONF-TEST-849201",
            "Submit another response"
        )
        val result = detector.detect(pageTexts)
        assertTrue(result is ConfirmationResult.Confirmed)
        val confirmed = result as ConfirmationResult.Confirmed
        assertEquals("CONF-TEST-849201", confirmed.reference)
        assertTrue(confirmed.message.contains("Your response has been recorded", ignoreCase = true))
    }

    @Test
    fun testConfirmationDetectorHandlesGenericConfirmationWithoutReference() {
        val pageTexts = listOf(
            "Thank you for submitting your application.",
            "We have received your response."
        )
        val result = detector.detect(pageTexts)
        assertTrue(result is ConfirmationResult.Confirmed)
        val confirmed = result as ConfirmationResult.Confirmed
        assertNotNull(confirmed.reference)
        assertTrue(confirmed.reference.startsWith("RECEIPT-"))
    }

    @Test
    fun testConfirmationDetectorRecognizesFormErrors() {
        val pageTexts = listOf(
            "Application Form",
            "This question requires an answer",
            "There was an error submitting the form"
        )
        val result = detector.detect(pageTexts)
        assertTrue(result is ConfirmationResult.SubmissionError)
        val error = result as ConfirmationResult.SubmissionError
        assertTrue(error.reason.contains("requires an answer") || error.reason.contains("error"))
    }

    @Test
    fun testConfirmationDetectorReturnsUnknownWhenPageAmbiguous() {
        val pageTexts = listOf("Loading...", "Please wait")
        val result = detector.detect(pageTexts)
        assertTrue(result is ConfirmationResult.Unknown)
    }

    @Test
    fun testSubmitFlowWithValidApprovalTokenTransitionsToSubmittedConfirmed() = runBlocking {
        val app = createTestRecord()
        val formStateHash = tokenManager.computeFormStateHash(mapOf("Name" to "John", "Email" to "john@example.com"))

        val tokenResult = tokenManager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = formStateHash,
            resumeHash = app.resumeHash!!
        ) as TokenIssuanceResult.Success

        val pageTexts = listOf(
            "Your response has been recorded.",
            "Reference: CONF-TEST-999888"
        )

        var submitButtonTapped = 0
        val outcome = supervisor.submit(
            applicationId = app.id,
            token = tokenResult.token,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = formStateHash,
            resumeHash = app.resumeHash!!,
            tapSubmitAction = { submitButtonTapped++ },
            fetchPostSubmitPageTexts = { pageTexts }
        )

        assertTrue(outcome is SubmitOutcome.Confirmed)
        assertEquals(1, submitButtonTapped)

        val updatedRecord = tracker.getApplication(app.id)
        assertNotNull(updatedRecord)
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, updatedRecord?.state)
        assertEquals("CONF-TEST-999888", updatedRecord?.confirmationReference)
    }

    @Test
    fun testSubmitFlowRejectsWhenTokenInvalidOrMissingDirectTap() = runBlocking {
        val app = createTestRecord()
        var submitButtonTapped = 0

        val forgedToken = ApprovalToken(
            tokenId = "forged",
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash1",
            resumeHash = "hash2",
            expiresAt = System.currentTimeMillis() + 60000
        )

        val outcome = supervisor.submit(
            applicationId = app.id,
            token = forgedToken,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash1",
            resumeHash = "hash2",
            tapSubmitAction = { submitButtonTapped++ },
            fetchPostSubmitPageTexts = { emptyList() }
        )

        assertTrue(outcome is SubmitOutcome.Rejected)
        assertEquals(0, submitButtonTapped) // Must NEVER tap without valid token
    }

    @Test
    fun testSubmitTimeoutTransitionsToSubmissionUnknownAndDoesNotAutoRetry() = runBlocking {
        val app = createTestRecord()
        val formStateHash = tokenManager.computeFormStateHash(mapOf("Name" to "John"))

        val tokenResult = tokenManager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = formStateHash,
            resumeHash = app.resumeHash!!
        ) as TokenIssuanceResult.Success

        var submitButtonTapped = 0
        val outcome = supervisor.submit(
            applicationId = app.id,
            token = tokenResult.token,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = formStateHash,
            resumeHash = app.resumeHash!!,
            tapSubmitAction = { submitButtonTapped++ },
            fetchPostSubmitPageTexts = { listOf("Submitting...", "Network timeout") }
        )

        assertTrue(outcome is SubmitOutcome.Unknown)
        assertEquals(1, submitButtonTapped) // Tapped once

        val updatedRecord = tracker.getApplication(app.id)
        assertNotNull(updatedRecord)
        assertEquals(ApplicationState.SUBMISSION_UNKNOWN, updatedRecord?.state)

        // Attempting to submit again with same token fails immediately
        val retryOutcome = supervisor.submit(
            applicationId = app.id,
            token = tokenResult.token,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = formStateHash,
            resumeHash = app.resumeHash!!,
            tapSubmitAction = { submitButtonTapped++ },
            fetchPostSubmitPageTexts = { emptyList() }
        )
        assertTrue(retryOutcome is SubmitOutcome.Rejected)
        assertEquals(1, submitButtonTapped) // No second tap
    }
}
