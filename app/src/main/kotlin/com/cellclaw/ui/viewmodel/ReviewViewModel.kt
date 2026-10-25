package com.cellclaw.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.accessibility.AccessibilityChecker
import com.cellclaw.approval.ApprovalToken
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.TokenIssuanceResult
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileRepository
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

data class FormFieldReviewItem(
    val label: String,
    val value: String,
    val confidence: Float = 1.0f,
    val isUncertain: Boolean = false,
    val sourceFactId: String? = null
)

data class NeedsInputReviewItem(
    val id: String,
    val label: String,
    val category: String,
    val description: String = "",
    val currentValue: String = "",
    val isResolved: Boolean = false
)

data class ReviewUiState(
    val isLoading: Boolean = true,
    val application: ApplicationRecord? = null,
    val company: String = "",
    val role: String = "",
    val domain: String = "N/A",
    val sourceUrl: String? = null,
    val resumeFileName: String = "",
    val resumeHash: String? = null,
    val resumeVersion: Int = 1,
    val filledFields: List<FormFieldReviewItem> = emptyList(),
    val unansweredFields: List<String> = emptyList(),
    val declarations: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val currentState: ApplicationState = ApplicationState.DRAFT,
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null,
    val approvalToken: ApprovalToken? = null,
    val showAccessibilityPrompt: Boolean = false,
    val needsInputItems: List<NeedsInputReviewItem> = emptyList(),
    val showNeedsInputModal: Boolean = false
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val applicationTracker: ApplicationTracker,
    private val profileRepository: ProfileRepository,
    private val tokenManager: ApprovalTokenManager = ApprovalTokenManager(),
    private val accessibilityChecker: AccessibilityChecker? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    companion object {
        const val KEY_APP_ID = "appId"
        const val KEY_ACTION_SUCCESS = "key_action_success"
    }

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        val appId: String? = savedStateHandle[KEY_APP_ID]
        if (!appId.isNullOrBlank()) {
            load(appId)
        }
    }

    fun load(appId: String) {
        savedStateHandle[KEY_APP_ID] = appId
        viewModelScope.launch {
            _uiState.value = ReviewUiState(isLoading = true)
            try {
                val app = applicationTracker.getApplication(appId)
                if (app == null) {
                    _uiState.value = ReviewUiState(
                        isLoading = false,
                        errorMessage = "Application not found: $appId"
                    )
                    return@launch
                }

                val profile = profileRepository.getProfile() ?: CandidateProfile()

                // Extract domain from sourceUrl
                val domain = app.sourceUrl?.let { url ->
                    try {
                        val uri = URI(url)
                        uri.host ?: url
                    } catch (e: Exception) {
                        url
                    }
                } ?: "No URL (Local JD)"

                val resumeFileName = "resume_${app.company.lowercase().replace("\\s+".toRegex(), "_")}_v${app.resumeVersion}.pdf"

                // Synthesize form field values from confirmed profile facts
                val filledFields = mutableListOf<FormFieldReviewItem>()
                val uncertainties = mutableListOf<String>()
                val unanswered = mutableListOf<String>()

                val nameFact = profile.name?.takeIf { it.confirmed }
                if (nameFact != null && nameFact.value.isNotBlank()) {
                    filledFields.add(FormFieldReviewItem("Full Name", nameFact.value, 1.0f, sourceFactId = nameFact.id))
                } else {
                    uncertainties.add("Full Name is missing or unconfirmed in profile.")
                    unanswered.add("Full Name")
                }

                val emailFact = profile.email?.takeIf { it.confirmed }
                if (emailFact != null && emailFact.value.isNotBlank()) {
                    filledFields.add(FormFieldReviewItem("Email Address", emailFact.value, 1.0f, sourceFactId = emailFact.id))
                } else {
                    uncertainties.add("Email address is missing or unconfirmed in profile.")
                    unanswered.add("Email Address")
                }

                val phoneFact = profile.phone?.takeIf { it.confirmed }
                if (phoneFact != null && phoneFact.value.isNotBlank()) {
                    filledFields.add(FormFieldReviewItem("Phone Number", phoneFact.value, 1.0f, sourceFactId = phoneFact.id))
                } else {
                    uncertainties.add("Phone number is missing or unconfirmed in profile.")
                    unanswered.add("Phone Number")
                }

                profile.college?.takeIf { it.confirmed && it.value.isNotBlank() }?.let {
                    filledFields.add(FormFieldReviewItem("College / University", it.value, 1.0f, sourceFactId = it.id))
                }

                profile.degree?.takeIf { it.confirmed && it.value.isNotBlank() }?.let {
                    filledFields.add(FormFieldReviewItem("Degree", it.value, 1.0f, sourceFactId = it.id))
                }

                profile.branch?.takeIf { it.confirmed && it.value.isNotBlank() }?.let {
                    filledFields.add(FormFieldReviewItem("Branch / Major", it.value, 1.0f, sourceFactId = it.id))
                }

                profile.cgpa?.takeIf { it.confirmed && it.value.isNotBlank() }?.let {
                    val scaleStr = profile.cgpaScale?.takeIf { s -> s.confirmed }?.value?.let { s -> "/$s" } ?: "/10.0"
                    filledFields.add(FormFieldReviewItem("Current CGPA", "${it.value}$scaleStr", 1.0f, sourceFactId = it.id))
                }

                profile.graduationYear?.takeIf { it.confirmed && it.value.isNotBlank() }?.let {
                    filledFields.add(FormFieldReviewItem("Graduation Year", it.value, 1.0f, sourceFactId = it.id))
                }

                profile.links.filter { it.confirmed && it.value.isNotBlank() }.forEach { link ->
                    filledFields.add(FormFieldReviewItem("Link (${link.field})", link.value, 1.0f, sourceFactId = link.id))
                }

                // Check eligibility status
                if (app.eligibilityResult.uppercase() != "ELIGIBLE") {
                    uncertainties.add("Eligibility check resulted in ${app.eligibilityResult} — manual review advised before submission.")
                }

                // Check source URL protocol
                if (app.sourceUrl != null && !app.sourceUrl.startsWith("https://")) {
                    uncertainties.add("Form URL is not secure (does not use https://): ${app.sourceUrl}")
                }

                val declarations = listOf(
                    "I confirm that all provided details and resume contents are strictly derived from confirmed profile facts.",
                    "I approve submitting this application to $domain on my behalf."
                )

                val needsInputItems = mutableListOf<NeedsInputReviewItem>()
                unanswered.forEach { fieldName ->
                    val fieldId = "missing_${fieldName.lowercase().replace(" ", "_").replace("/", "_")}"
                    val savedVal: String? = savedStateHandle["resolved_$fieldId"]
                    needsInputItems.add(
                        NeedsInputReviewItem(
                            id = fieldId,
                            label = fieldName,
                            category = "Missing Profile Fact",
                            description = "Required profile fact is missing or unconfirmed. Enter a value to fill this field.",
                            currentValue = savedVal ?: "",
                            isResolved = !savedVal.isNullOrBlank()
                        )
                    )
                }

                // Add sensitive placement questions requiring explicit user approval
                val sensitiveQuestions = listOf(
                    NeedsInputReviewItem(
                        id = "sensitive_stipend_ctc",
                        label = "Expected CTC / Stipend",
                        category = "Sensitive Compensation Question",
                        description = "PlacementOS rule: salary expectations must never be auto-filled without explicit approval."
                    ),
                    NeedsInputReviewItem(
                        id = "sensitive_service_bond",
                        label = "Service Bond / Agreement",
                        category = "Legal / Bond Declaration",
                        description = "PlacementOS rule: bond or legal commitment terms require explicit user review."
                    )
                )

                sensitiveQuestions.forEach { item ->
                    val savedVal: String? = savedStateHandle["resolved_${item.id}"]
                    needsInputItems.add(
                        item.copy(
                            currentValue = savedVal ?: "",
                            isResolved = !savedVal.isNullOrBlank()
                        )
                    )
                }

                val activeFilledFields = filledFields.toMutableList()
                val activeUnanswered = unanswered.toMutableList()
                val activeUncertainties = uncertainties.toMutableList()

                needsInputItems.filter { it.isResolved }.forEach { resolved ->
                    activeFilledFields.removeAll { it.label.equals(resolved.label, ignoreCase = true) }
                    activeFilledFields.add(FormFieldReviewItem(resolved.label, resolved.currentValue, 1.0f))
                    activeUnanswered.removeAll { it.equals(resolved.label, ignoreCase = true) }
                    activeUncertainties.removeAll { it.contains(resolved.label, ignoreCase = true) }
                }

                val restoredSuccess: String? = savedStateHandle[KEY_ACTION_SUCCESS]
                _uiState.value = ReviewUiState(
                    isLoading = false,
                    application = app,
                    company = app.company,
                    role = app.role,
                    domain = domain,
                    sourceUrl = app.sourceUrl,
                    resumeFileName = resumeFileName,
                    resumeHash = app.resumeHash,
                    resumeVersion = app.resumeVersion,
                    filledFields = activeFilledFields,
                    unansweredFields = activeUnanswered,
                    declarations = declarations,
                    uncertainties = activeUncertainties,
                    currentState = app.state,
                    actionSuccessMessage = restoredSuccess,
                    needsInputItems = needsInputItems
                )
            } catch (e: Exception) {
                _uiState.value = ReviewUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load application review"
                )
            }
        }
    }

    fun openNeedsInputModal() {
        _uiState.value = _uiState.value.copy(showNeedsInputModal = true)
    }

    fun dismissNeedsInputModal() {
        _uiState.value = _uiState.value.copy(showNeedsInputModal = false)
    }

    fun resolveFieldInput(id: String, value: String) {
        val trimmed = value.trim()
        val currentItems = _uiState.value.needsInputItems
        val target = currentItems.find { it.id == id } ?: return

        savedStateHandle["resolved_$id"] = trimmed

        val updatedItems = currentItems.map {
            if (it.id == id) it.copy(currentValue = trimmed, isResolved = trimmed.isNotBlank()) else it
        }

        val updatedFilled = _uiState.value.filledFields.filterNot {
            it.label.equals(target.label, ignoreCase = true)
        }.toMutableList()

        if (trimmed.isNotBlank()) {
            updatedFilled.add(FormFieldReviewItem(label = target.label, value = trimmed, confidence = 1.0f))
        }

        val updatedUnanswered = if (trimmed.isNotBlank()) {
            _uiState.value.unansweredFields.filterNot { it.equals(target.label, ignoreCase = true) }
        } else {
            if (target.category == "Missing Profile Fact" && target.label !in _uiState.value.unansweredFields) {
                _uiState.value.unansweredFields + target.label
            } else _uiState.value.unansweredFields
        }

        val updatedUncertainties = if (trimmed.isNotBlank()) {
            _uiState.value.uncertainties.filterNot { it.contains(target.label, ignoreCase = true) }
        } else _uiState.value.uncertainties

        _uiState.value = _uiState.value.copy(
            needsInputItems = updatedItems,
            filledFields = updatedFilled,
            unansweredFields = updatedUnanswered,
            uncertainties = updatedUncertainties
        )
    }

    fun approveSubmission(
        userDirectTapConfirmed: Boolean = true,
        onApproved: (ApprovalToken) -> Unit = {}
    ) {
        val app = _uiState.value.application ?: return

        // Verify that Elixir Accessibility Service is enabled before launching automation
        if (accessibilityChecker != null && !accessibilityChecker.isEnabled()) {
            _uiState.value = _uiState.value.copy(showAccessibilityPrompt = true)
            return
        }

        viewModelScope.launch {
            try {
                val urlOrigin = app.sourceUrl?.let { url ->
                    try {
                        val uri = URI(url)
                        val scheme = uri.scheme ?: "https"
                        val host = uri.host ?: url
                        val portStr = if (uri.port != -1) ":${uri.port}" else ""
                        "$scheme://$host$portStr"
                    } catch (e: Exception) {
                        url
                    }
                } ?: "local"

                val formMap = _uiState.value.filledFields.associate { it.label to it.value }
                val formHash = tokenManager.computeFormStateHash(formMap)
                val resumeHash = app.resumeHash ?: "NO_RESUME_HASH"

                val tokenResult = tokenManager.issueToken(
                    userDirectTapConfirmed = userDirectTapConfirmed,
                    applicationId = app.id,
                    company = app.company,
                    role = app.role,
                    urlOrigin = urlOrigin,
                    formStateHash = formHash,
                    resumeHash = resumeHash
                )

                when (tokenResult) {
                    is TokenIssuanceResult.Rejected -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Approval rejected: ${tokenResult.reason}"
                        )
                    }
                    is TokenIssuanceResult.Success -> {
                        val token = tokenResult.token
                        // If in DRAFT, transition to READY_FOR_REVIEW.
                        // Strictly do NOT transition to SUBMITTING until automation actually launches.
                        val updated = if (app.state == ApplicationState.DRAFT) {
                            applicationTracker.transitionState(
                                app.id,
                                ApplicationState.READY_FOR_REVIEW,
                                "User approved submission; ApprovalToken issued: ${token.tokenId.take(8)}"
                            )
                        } else {
                            app
                        }

                        val msg = "Approved! Approval token generated. Status: ${updated.state}."
                        savedStateHandle[KEY_ACTION_SUCCESS] = msg
                        _uiState.value = _uiState.value.copy(
                            application = updated,
                            currentState = updated.state,
                            approvalToken = token,
                            actionSuccessMessage = msg,
                            errorMessage = null
                        )
                        onApproved(token)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Approval failed: ${e.message}"
                )
            }
        }
    }

    fun markManualConfirmed(reference: String) {
        val app = _uiState.value.application ?: return
        viewModelScope.launch {
            try {
                // Need to be in a state that can transition to SUBMITTED_CONFIRMED
                // Transition chain if needed: DRAFT -> READY_FOR_REVIEW -> SUBMITTING -> SUBMITTED_CONFIRMED
                var cur = app
                if (cur.state == ApplicationState.DRAFT) {
                    cur = applicationTracker.transitionState(cur.id, ApplicationState.READY_FOR_REVIEW)
                }
                if (cur.state == ApplicationState.READY_FOR_REVIEW) {
                    cur = applicationTracker.transitionState(cur.id, ApplicationState.SUBMITTING)
                }
                val confirmed = applicationTracker.transitionState(
                    id = cur.id,
                    targetState = ApplicationState.SUBMITTED_CONFIRMED,
                    notes = "Manually confirmed by user",
                    confirmationReference = reference.ifBlank { "MANUAL-${System.currentTimeMillis()}" }
                )
                val msg = "Application marked as SUBMITTED_CONFIRMED (Ref: ${confirmed.confirmationReference})."
                savedStateHandle[KEY_ACTION_SUCCESS] = msg
                _uiState.value = _uiState.value.copy(
                    application = confirmed,
                    currentState = confirmed.state,
                    actionSuccessMessage = msg
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to confirm: ${e.message}"
                )
            }
        }
    }

    fun dismissAccessibilityPrompt() {
        _uiState.value = _uiState.value.copy(showAccessibilityPrompt = false)
    }

    fun openAccessibilitySettings(context: android.content.Context) {
        _uiState.value = _uiState.value.copy(showAccessibilityPrompt = false)
        try {
            val intent = accessibilityChecker?.createSettingsIntent(context)
                ?: android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Could not open Accessibility settings: ${e.message}"
            )
        }
    }
}
