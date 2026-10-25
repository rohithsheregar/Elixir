package com.cellclaw.approval

sealed interface ConfirmationResult {
    data class Confirmed(val reference: String, val message: String) : ConfirmationResult
    data class SubmissionError(val reason: String) : ConfirmationResult
    data object Unknown : ConfirmationResult
}

class ConfirmationDetector {

    companion object {
        private val CONFIRMATION_PHRASES = listOf(
            "your response has been recorded",
            "thank you for submitting",
            "we have received your response",
            "response recorded",
            "application submitted",
            "application received",
            "thank you for your response",
            "your response was submitted"
        )

        private val ERROR_PHRASES = listOf(
            "this question requires an answer",
            "there was an error",
            "form submission failed",
            "please correct the errors",
            "failed to submit"
        )

        private val REF_PATTERNS = listOf(
            Regex("""CONF-[A-Za-z0-9-]+"""),
            Regex("""(?:reference|ref|confirmation\s*id|receipt)[\s:#]+([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Inspects visible texts from the target browser page following a submit action.
     */
    fun detect(pageTexts: List<String>): ConfirmationResult {
        val fullText = pageTexts.joinToString(" ")
        val lowerText = fullText.lowercase()

        // Check for submission errors first
        for (errorPhrase in ERROR_PHRASES) {
            if (lowerText.contains(errorPhrase)) {
                return ConfirmationResult.SubmissionError(
                    "Form error detected: $errorPhrase"
                )
            }
        }

        // Check for confirmation phrases
        val matchedConfirmation = CONFIRMATION_PHRASES.firstOrNull { lowerText.contains(it) }
        if (matchedConfirmation != null) {
            // Attempt to extract reference ID
            var reference: String? = null
            for (pattern in REF_PATTERNS) {
                val match = pattern.find(fullText)
                if (match != null) {
                    reference = if (match.groupValues.size > 1) {
                        match.groupValues[1]
                    } else {
                        match.value
                    }
                    break
                }
            }

            val finalRef = reference ?: "RECEIPT-${System.currentTimeMillis()}"
            return ConfirmationResult.Confirmed(
                reference = finalRef,
                message = "Submission confirmed: $matchedConfirmation"
            )
        }

        return ConfirmationResult.Unknown
    }
}
