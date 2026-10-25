package com.cellclaw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val actionSuccessMessage: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val applicationTracker: ApplicationTracker,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    fun load(appId: String) {
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
                    filledFields = filledFields,
                    unansweredFields = unanswered,
                    declarations = declarations,
                    uncertainties = uncertainties,
                    currentState = app.state
                )
            } catch (e: Exception) {
                _uiState.value = ReviewUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load application review"
                )
            }
        }
    }

    fun approveSubmission(onApproved: () -> Unit = {}) {
        val app = _uiState.value.application ?: return
        viewModelScope.launch {
            try {
                // If in DRAFT, transition to READY_FOR_REVIEW then SUBMITTING
                val intermediate = if (app.state == ApplicationState.DRAFT) {
                    applicationTracker.transitionState(app.id, ApplicationState.READY_FOR_REVIEW, "User reviewed and approved for submission")
                } else {
                    app
                }

                val updated = if (intermediate.state.canTransitionTo(ApplicationState.SUBMITTING)) {
                    applicationTracker.transitionState(intermediate.id, ApplicationState.SUBMITTING, "Submission initiated by user")
                } else {
                    intermediate
                }

                _uiState.value = _uiState.value.copy(
                    application = updated,
                    currentState = updated.state,
                    actionSuccessMessage = "Approved! Status transitioned to ${updated.state}."
                )
                onApproved()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Transition failed: ${e.message}"
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
                _uiState.value = _uiState.value.copy(
                    application = confirmed,
                    currentState = confirmed.state,
                    actionSuccessMessage = "Application marked as SUBMITTED_CONFIRMED (Ref: ${confirmed.confirmationReference})."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to confirm: ${e.message}"
                )
            }
        }
    }
}
