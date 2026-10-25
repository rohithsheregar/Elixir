package com.cellclaw.upload

sealed interface PostUploadVerificationResult {
    data class Verified(val fileName: String) : PostUploadVerificationResult
    data class NeedsVisualConfirmation(val reason: String) : PostUploadVerificationResult
    data class Mismatch(val expectedFileName: String, val actualVisibleText: String) : PostUploadVerificationResult
}

/**
 * Verifies that the uploaded resume filename appears on the target form page.
 * If page provides no clear signal, prompts the user to visually confirm.
 */
class PostUploadVerifier {

    fun verify(visibleTexts: List<String>, expectedFileName: String): PostUploadVerificationResult {
        if (expectedFileName.isBlank()) {
            return PostUploadVerificationResult.NeedsVisualConfirmation("Expected file name is blank.")
        }
        val cleanExpected = expectedFileName.trim().lowercase()

        for (text in visibleTexts) {
            val cleanText = text.trim().lowercase()
            if (cleanText == cleanExpected || cleanText.contains(cleanExpected) || cleanExpected.contains(cleanText)) {
                return PostUploadVerificationResult.Verified(expectedFileName)
            }
        }

        // Check if a different document/pdf filename is visible
        val otherFile = visibleTexts.firstOrNull {
            (it.endsWith(".pdf", ignoreCase = true) || it.endsWith(".docx", ignoreCase = true)) &&
            !it.contains("example", ignoreCase = true)
        }
        if (otherFile != null) {
            return PostUploadVerificationResult.Mismatch(expectedFileName, otherFile)
        }

        return PostUploadVerificationResult.NeedsVisualConfirmation(
            "No matching filename found in page nodes. User visual confirmation required."
        )
    }
}
