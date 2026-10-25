package com.cellclaw.approval

import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SubmitOutcome {
    data class Confirmed(val reference: String, val message: String) : SubmitOutcome
    data class Unknown(val reason: String) : SubmitOutcome
    data class Failed(val error: String) : SubmitOutcome
    data class Rejected(val reason: String) : SubmitOutcome
}

/**
 * Supervised submission manager.
 * Enforces the strict rule: the final Submit button can only be tapped once,
 * strictly after explicit user approval backed by a valid ApprovalToken.
 * Updates the application tracker state accordingly, and strictly prohibits automatic re-submission.
 */
@Singleton
class SubmitSupervisor @Inject constructor(
    private val tokenManager: ApprovalTokenManager,
    private val tracker: ApplicationTracker,
    private val detector: ConfirmationDetector
) {

    suspend fun submit(
        applicationId: String,
        token: ApprovalToken,
        company: String,
        role: String,
        urlOrigin: String,
        formStateHash: String,
        resumeHash: String,
        tapSubmitAction: suspend () -> Unit,
        fetchPostSubmitPageTexts: suspend () -> List<String>
    ): SubmitOutcome {
        // 1. Validate the approval token
        val validation = tokenManager.validateToken(
            tokenId = token.tokenId,
            company = company,
            role = role,
            urlOrigin = urlOrigin,
            formStateHash = formStateHash,
            resumeHash = resumeHash
        )

        if (validation !is TokenValidationResult.Valid) {
            return SubmitOutcome.Rejected("Submission rejected: Approval token is invalid ($validation).")
        }

        // 2. Consume the token (single use only)
        val consumed = tokenManager.consumeToken(token.tokenId)
        if (!consumed) {
            return SubmitOutcome.Rejected("Submission rejected: Approval token has already been consumed.")
        }

        // 3. Transition tracker to SUBMITTING
        val existing = tracker.getApplication(applicationId)
        if (existing != null && existing.state.canTransitionTo(ApplicationState.SUBMITTING)) {
            tracker.transitionState(applicationId, ApplicationState.SUBMITTING, "Submission initiated with verified user approval token.")
        }

        // 4. Tap the submit button once
        try {
            tapSubmitAction()
        } catch (e: Exception) {
            tracker.transitionState(applicationId, ApplicationState.FAILED, "Submit button action failed: ${e.message}")
            return SubmitOutcome.Failed("Submit action threw exception: ${e.message}")
        }

        // 5. Inspect post-submit page state
        val postSubmitTexts = try {
            fetchPostSubmitPageTexts()
        } catch (_: Exception) {
            emptyList()
        }

        // 6. Detect outcome and update tracker
        return when (val result = detector.detect(postSubmitTexts)) {
            is ConfirmationResult.Confirmed -> {
                tracker.transitionState(
                    id = applicationId,
                    targetState = ApplicationState.SUBMITTED_CONFIRMED,
                    notes = result.message,
                    confirmationReference = result.reference
                )
                SubmitOutcome.Confirmed(result.reference, result.message)
            }
            is ConfirmationResult.SubmissionError -> {
                tracker.transitionState(
                    id = applicationId,
                    targetState = ApplicationState.FAILED,
                    notes = result.reason
                )
                SubmitOutcome.Failed(result.reason)
            }
            is ConfirmationResult.Unknown -> {
                // Rule: On a timeout / ambiguous state, mark SUBMISSION_UNKNOWN and do NOT resubmit automatically.
                tracker.transitionState(
                    id = applicationId,
                    targetState = ApplicationState.SUBMISSION_UNKNOWN,
                    notes = "Post-submit page gave no definitive confirmation signal. Marked SUBMISSION_UNKNOWN; no automatic retry."
                )
                SubmitOutcome.Unknown("Confirmation signal not detected on post-submit page.")
            }
        }
    }
}
