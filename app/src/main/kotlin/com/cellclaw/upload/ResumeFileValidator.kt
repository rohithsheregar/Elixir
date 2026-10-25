package com.cellclaw.upload

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed interface ValidationResult {
    data class Valid(val sha256: String, val sizeBytes: Long) : ValidationResult
    data object FileNotFound : ValidationResult
    data object EmptyFile : ValidationResult
    data object InvalidMagicBytes : ValidationResult
    data class HashMismatch(val actualSha256: String, val expectedSha256: String) : ValidationResult
    data class FileTooLarge(val sizeBytes: Long, val maxSizeBytes: Long) : ValidationResult
}

class ResumeFileValidator {

    companion object {
        // PDF-1.x files always start with %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
        private val PDF_MAGIC_BYTES = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)
    }

    /**
     * Validates that [file] exists, has non-zero size, does not exceed [maxSizeBytes],
     * has valid PDF magic bytes ("%PDF-"), and its SHA-256 matches [expectedSha256].
     */
    fun validate(
        file: File,
        expectedSha256: String,
        maxSizeBytes: Long? = null
    ): ValidationResult {
        if (!file.exists() || !file.isFile) {
            return ValidationResult.FileNotFound
        }

        val length = file.length()
        if (length == 0L) {
            return ValidationResult.EmptyFile
        }

        if (maxSizeBytes != null && length > maxSizeBytes) {
            return ValidationResult.FileTooLarge(length, maxSizeBytes)
        }

        // Check magic bytes
        val magicBuffer = ByteArray(PDF_MAGIC_BYTES.size)
        try {
            FileInputStream(file).use { input ->
                val read = input.read(magicBuffer)
                if (read < PDF_MAGIC_BYTES.size || !magicBuffer.contentEquals(PDF_MAGIC_BYTES)) {
                    return ValidationResult.InvalidMagicBytes
                }
            }
        } catch (_: Exception) {
            return ValidationResult.InvalidMagicBytes
        }

        // Compute SHA-256
        val actualSha256 = try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            return ValidationResult.FileNotFound
        }

        if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
            return ValidationResult.HashMismatch(actualSha256, expectedSha256)
        }

        return ValidationResult.Valid(sha256 = actualSha256, sizeBytes = length)
    }
}
