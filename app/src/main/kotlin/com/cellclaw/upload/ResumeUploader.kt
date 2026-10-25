package com.cellclaw.upload

import com.cellclaw.tracker.ApplicationRecord
import java.io.File

sealed interface PickerResult {
    data class Selected(val uri: String) : PickerResult
    data object Cancelled : PickerResult
    data object PermissionDenied : PickerResult
}

sealed interface ServerUploadResponse {
    data class Accepted(val fileName: String) : ServerUploadResponse
    data class FileTooLarge(val sizeBytes: Long, val limitBytes: Long) : ServerUploadResponse
    data class Rejected(val reason: String) : ServerUploadResponse
    data object Timeout : ServerUploadResponse
}

sealed interface ResumePreparationResult {
    data class Success(
        val contentUri: String,
        val fileName: String,
        val sha256: String,
        val sizeBytes: Long
    ) : ResumePreparationResult

    data class Failure(val reason: String) : ResumePreparationResult
}

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data class ReadyForPicker(val contentUri: String, val fileName: String, val sha256: String) : UploadStatus
    data class Uploading(val contentUri: String, val fileName: String) : UploadStatus
    data class Confirmed(val fileName: String, val sha256: String) : UploadStatus
    data class Cancelled(val reason: String = "User cancelled file picker") : UploadStatus
    data object PermissionDenied : UploadStatus
    data class TooLarge(val sizeBytes: Long, val limitBytes: Long) : UploadStatus
    data class ServerRejected(val reason: String) : UploadStatus
    data object Timeout : UploadStatus
    data class Failed(val reason: String) : UploadStatus
}

/**
 * Supervised resume uploader coordinator.
 * Ensures that only verified company-specific resumes matching the tracker record's SHA-256
 * are exposed via FileProvider and uploaded. Handles cancellations, timeouts, and server errors.
 */
class ResumeUploader(
    private val validator: ResumeFileValidator = ResumeFileValidator(),
    private val uriProvider: ResumeUriProvider
) {
    private var currentRecord: ApplicationRecord? = null
    private var preparedFile: File? = null
    private var preparedSha256: String? = null
    private var currentStatus: UploadStatus = UploadStatus.Idle

    val status: UploadStatus get() = currentStatus

    /**
     * Prepares and validates a resume file for upload against the given [record].
     */
    fun prepareUpload(
        file: File,
        record: ApplicationRecord,
        maxSizeBytes: Long? = null
    ): ResumePreparationResult {
        currentRecord = record
        val expectedHash = record.resumeHash
        if (expectedHash.isNullOrBlank()) {
            val failure = "Application record ${record.id} has no resume hash set."
            currentStatus = UploadStatus.Failed(failure)
            return ResumePreparationResult.Failure(failure)
        }

        when (val validation = validator.validate(file, expectedHash, maxSizeBytes)) {
            is ValidationResult.FileNotFound -> {
                val failure = "Resume file not found: ${file.absolutePath}"
                currentStatus = UploadStatus.Failed(failure)
                return ResumePreparationResult.Failure(failure)
            }
            is ValidationResult.EmptyFile -> {
                val failure = "Resume file is empty (0 bytes)."
                currentStatus = UploadStatus.Failed(failure)
                return ResumePreparationResult.Failure(failure)
            }
            is ValidationResult.InvalidMagicBytes -> {
                val failure = "File does not contain valid PDF magic bytes (%PDF-)."
                currentStatus = UploadStatus.Failed(failure)
                return ResumePreparationResult.Failure(failure)
            }
            is ValidationResult.HashMismatch -> {
                val failure = "Hash mismatch: actual ${validation.actualSha256} vs expected ${validation.expectedSha256}"
                currentStatus = UploadStatus.Failed(failure)
                return ResumePreparationResult.Failure(failure)
            }
            is ValidationResult.FileTooLarge -> {
                val failure = "File size (${validation.sizeBytes} bytes) exceeds limit (${validation.maxSizeBytes} bytes)."
                currentStatus = UploadStatus.TooLarge(validation.sizeBytes, validation.maxSizeBytes)
                return ResumePreparationResult.Failure(failure)
            }
            is ValidationResult.Valid -> {
                val uri = uriProvider.getSharableUri(file)
                preparedFile = file
                preparedSha256 = validation.sha256
                currentStatus = UploadStatus.ReadyForPicker(uri, file.name, validation.sha256)
                return ResumePreparationResult.Success(
                    contentUri = uri,
                    fileName = file.name,
                    sha256 = validation.sha256,
                    sizeBytes = validation.sizeBytes
                )
            }
        }
    }

    /**
     * Handles system picker selection or cancellation.
     */
    fun handlePickerResult(result: PickerResult): UploadStatus {
        currentStatus = when (result) {
            is PickerResult.Cancelled -> UploadStatus.Cancelled("User cancelled file picker")
            is PickerResult.PermissionDenied -> UploadStatus.PermissionDenied
            is PickerResult.Selected -> {
                val file = preparedFile
                if (file != null) {
                    UploadStatus.Uploading(result.uri, file.name)
                } else {
                    UploadStatus.Failed("No prepared file found for upload.")
                }
            }
        }
        return currentStatus
    }

    /**
     * Handles server upload responses (success, rejected, too large, timeout).
     */
    fun handleServerResponse(response: ServerUploadResponse): UploadStatus {
        currentStatus = when (response) {
            is ServerUploadResponse.Accepted -> {
                val sha = preparedSha256 ?: ""
                UploadStatus.Confirmed(response.fileName, sha)
            }
            is ServerUploadResponse.FileTooLarge -> {
                UploadStatus.TooLarge(response.sizeBytes, response.limitBytes)
            }
            is ServerUploadResponse.Rejected -> {
                UploadStatus.ServerRejected(response.reason)
            }
            is ServerUploadResponse.Timeout -> {
                UploadStatus.Timeout
            }
        }
        return currentStatus
    }

    /**
     * Verifies whether the attached filename on the form page matches the expected filename.
     */
    fun verifyAttachedFileName(visibleFileName: String, expectedFileName: String): Boolean {
        if (visibleFileName.isBlank() || expectedFileName.isBlank()) return false
        val cleanVisible = visibleFileName.trim().lowercase()
        val cleanExpected = expectedFileName.trim().lowercase()
        return cleanVisible == cleanExpected || cleanVisible.contains(cleanExpected) || cleanExpected.contains(cleanVisible)
    }
}
