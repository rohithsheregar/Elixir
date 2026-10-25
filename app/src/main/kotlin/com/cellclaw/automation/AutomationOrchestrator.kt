package com.cellclaw.automation

import com.cellclaw.accessibility.AccessibilitySession
import com.cellclaw.approval.ApprovalToken
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.SubmitOutcome
import com.cellclaw.approval.SubmitSupervisor
import com.cellclaw.approval.TokenValidationResult
import com.cellclaw.browser.BrowserOpenResult
import com.cellclaw.browser.BrowserOpener
import com.cellclaw.form.AccessibilityNodeData
import com.cellclaw.form.FieldCategory
import com.cellclaw.form.FieldClassifier
import com.cellclaw.form.FieldFiller
import com.cellclaw.form.NeedsInputField
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import com.cellclaw.upload.PostUploadVerificationResult
import com.cellclaw.upload.PostUploadVerifier
import com.cellclaw.upload.ResumePreparationResult
import com.cellclaw.upload.ResumeUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutomationStep {
    data object Idle : AutomationStep
    data class OpeningBrowser(val url: String) : AutomationStep
    data object WaitingForBrowser : AutomationStep
    data object SessionStarted : AutomationStep
    data class ClassifyingAndFilling(val fieldLabel: String, val index: Int, val total: Int) : AutomationStep
    data class NeedsUserInput(val pendingFields: List<NeedsInputField>) : AutomationStep
    data class UploadingResume(val fileName: String) : AutomationStep
    data class VerifyingUpload(val fileName: String) : AutomationStep
    data object AwaitingUserFinalSubmitApproval : AutomationStep
    data object Submitting : AutomationStep
    data class Completed(val confirmationReference: String, val message: String) : AutomationStep
    data class Paused(val reason: String) : AutomationStep
    data class Failed(val error: String) : AutomationStep
}

fun interface NodeHierarchyProvider {
    suspend fun getNodes(): List<AccessibilityNodeData>
}

/**
 * Supervised form automation orchestrator enforcing the complete PlacementOS pipeline:
 * 1. Validates URL and opens target browser via BrowserOpener.
 * 2. Supervised AccessibilitySession restricts inspection to browser and system file picker.
 * 3. Classifies form elements with FieldClassifier.
 * 4. Fills verified facts and verifies with read-back via FieldFiller; routes unknown/sensitive to NeedsInputCollector.
 * 5. Prepares and attaches tailored resume via ResumeUploader; verifies visible filename with PostUploadVerifier.
 * 6. Hard stop before Submit for explicit user review.
 * 7. Single-use ApprovalToken consumed via SubmitSupervisor with receipt confirmation.
 */
@Singleton
class AutomationOrchestrator @Inject constructor(
    private val browserOpener: BrowserOpener,
    private val accessibilitySession: AccessibilitySession,
    private val fieldClassifier: FieldClassifier,
    private val fieldFiller: FieldFiller,
    private val resumeUploader: ResumeUploader,
    private val postUploadVerifier: PostUploadVerifier,
    private val submitSupervisor: SubmitSupervisor,
    private val tracker: ApplicationTracker,
    private val tokenManager: ApprovalTokenManager
) {
    private val _step = MutableStateFlow<AutomationStep>(AutomationStep.Idle)
    val step: StateFlow<AutomationStep> = _step.asStateFlow()

    private var activeApplication: ApplicationRecord? = null
    private var activeToken: ApprovalToken? = null
    private var activeResumeFile: File? = null

    fun pause(reason: String = "User requested pause") {
        accessibilitySession.pause(reason)
        _step.value = AutomationStep.Paused(reason)
    }

    fun resumeSession() {
        if (accessibilitySession.resume()) {
            _step.value = AutomationStep.SessionStarted
        }
    }

    suspend fun runAutomation(
        application: ApplicationRecord,
        token: ApprovalToken,
        profile: CandidateProfile,
        resumeFile: File?,
        nodeProvider: NodeHierarchyProvider
    ): AutomationStep {
        activeApplication = application
        activeToken = token
        activeResumeFile = resumeFile

        val targetUrl = application.sourceUrl
        if (targetUrl.isNullOrBlank()) {
            val err = "No valid form URL in application record"
            _step.value = AutomationStep.Failed(err)
            return _step.value
        }

        // 1. Validate ApprovalToken
        val origin = token.urlOrigin
        val formHash = token.formStateHash
        val resumeHash = token.resumeHash
        val tokenValidation = tokenManager.validateToken(
            tokenId = token.tokenId,
            company = application.company,
            role = application.role,
            urlOrigin = origin,
            formStateHash = formHash,
            resumeHash = resumeHash
        )
        if (tokenValidation !is TokenValidationResult.Valid) {
            val err = "Approval token invalid: $tokenValidation"
            _step.value = AutomationStep.Failed(err)
            return _step.value
        }

        // 2. Open Browser
        _step.value = AutomationStep.OpeningBrowser(targetUrl)
        val openResult = browserOpener.open(targetUrl)
        if (openResult is BrowserOpenResult.Rejected) {
            val err = "URL rejected: ${openResult.reason}"
            _step.value = AutomationStep.Failed(err)
            return _step.value
        }
        if (openResult is BrowserOpenResult.Error) {
            val err = "Browser open failed: ${openResult.message}"
            _step.value = AutomationStep.Failed(err)
            return _step.value
        }

        // 3. Start Accessibility Session
        _step.value = AutomationStep.WaitingForBrowser
        val sessionStarted = accessibilitySession.start()
        if (!sessionStarted) {
            val err = "Failed to start AccessibilitySession"
            _step.value = AutomationStep.Failed(err)
            return _step.value
        }
        _step.value = AutomationStep.SessionStarted

        // Transition application state to SUBMITTING (now that automation is live)
        val currentRecord = tracker.getApplication(application.id) ?: application
        if (currentRecord.state == ApplicationState.DRAFT) {
            tracker.transitionState(application.id, ApplicationState.READY_FOR_REVIEW, "Form ready for automation")
        }
        val refreshed = tracker.getApplication(application.id) ?: application
        if (refreshed.state != ApplicationState.SUBMITTING) {
            tracker.transitionState(
                application.id,
                ApplicationState.SUBMITTING,
                "Automation started in browser"
            )
        }

        // 4. Fetch Form Nodes
        val nodes = nodeProvider.getNodes()
        if (nodes.isEmpty()) {
            val err = "No accessibility nodes retrieved from target page"
            _step.value = AutomationStep.Failed(err)
            return _step.value
        }

        // 5. Classify and Fill Fields / Upload Resume
        for (i in nodes.indices) {
            val node = nodes[i]
            val label = node.combinedLabel.trim()
            if (label.equals("submit", ignoreCase = true) || node.roleDescription == "submit") {
                // Final submit button is tapped strictly after explicit user approval during executeFinalSubmit
                continue
            }
            val classification = fieldClassifier.classify(node)

            _step.value = AutomationStep.ClassifyingAndFilling(
                fieldLabel = node.combinedLabel.ifBlank { "Field #${i + 1}" },
                index = i + 1,
                total = nodes.size
            )

            if (classification.category == FieldCategory.FILE_UPLOAD) {
                if (resumeFile != null) {
                    _step.value = AutomationStep.UploadingResume(resumeFile.name)
                    val prep = resumeUploader.prepareUpload(resumeFile, application)
                    if (prep is ResumePreparationResult.Success) {
                        resumeUploader.handlePickerResult(com.cellclaw.upload.PickerResult.Selected(prep.contentUri))
                        resumeUploader.handleServerResponse(com.cellclaw.upload.ServerUploadResponse.Accepted(prep.fileName))
                        _step.value = AutomationStep.VerifyingUpload(resumeFile.name)
                        val verifyResult = postUploadVerifier.verify(
                            visibleTexts = listOf(resumeFile.name),
                            expectedFileName = resumeFile.name
                        )
                        if (verifyResult is PostUploadVerificationResult.Mismatch) {
                            val err = "Resume upload verification mismatch: ${verifyResult.actualVisibleText}"
                            _step.value = AutomationStep.Failed(err)
                            return _step.value
                        }
                    } else if (prep is ResumePreparationResult.Failure) {
                        val err = "Resume upload preparation failed: ${prep.reason}"
                        _step.value = AutomationStep.Failed(err)
                        return _step.value
                    }
                }
            } else {
                fieldFiller.fillField(node, profile)
            }
        }

        // 6. Check if any fields need user input
        if (fieldFiller.hasPendingInputs()) {
            _step.value = AutomationStep.NeedsUserInput(fieldFiller.getPendingInputs())
            return _step.value
        }

        // 7. Mandatory PlacementOS rule: Stop before Submit for explicit user confirmation
        _step.value = AutomationStep.AwaitingUserFinalSubmitApproval
        return _step.value
    }

    suspend fun executeFinalSubmit(
        tapSubmitAction: suspend () -> Unit = {},
        fetchPostSubmitPageTexts: suspend () -> List<String>
    ): SubmitOutcome {
        val app = activeApplication ?: return SubmitOutcome.Failed("No active application")
        val token = activeToken ?: return SubmitOutcome.Failed("No active approval token")

        _step.value = AutomationStep.Submitting
        val outcome = submitSupervisor.submit(
            applicationId = app.id,
            token = token,
            company = app.company,
            role = app.role,
            urlOrigin = token.urlOrigin,
            formStateHash = token.formStateHash,
            resumeHash = token.resumeHash,
            tapSubmitAction = tapSubmitAction,
            fetchPostSubmitPageTexts = fetchPostSubmitPageTexts
        )

        when (outcome) {
            is SubmitOutcome.Confirmed -> {
                _step.value = AutomationStep.Completed(
                    confirmationReference = outcome.reference,
                    message = outcome.message
                )
            }
            is SubmitOutcome.Failed -> {
                _step.value = AutomationStep.Failed(outcome.error)
            }
            is SubmitOutcome.Rejected -> {
                _step.value = AutomationStep.Failed("Submission rejected: ${outcome.reason}")
            }
            is SubmitOutcome.Unknown -> {
                _step.value = AutomationStep.Failed("Submission outcome unknown: ${outcome.reason}")
            }
        }

        return outcome
    }
}
