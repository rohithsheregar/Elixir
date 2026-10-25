package com.cellclaw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileRepository
import com.cellclaw.resume.GeneratedPdf
import com.cellclaw.resume.PdfGenerator
import com.cellclaw.resume.ResumeBuilder
import com.cellclaw.resume.ResumeContent
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResumePreviewUiState(
    val isLoading: Boolean = true,
    val application: ApplicationRecord? = null,
    val resume: ResumeContent? = null,
    val pdf: GeneratedPdf? = null,
    val materialChangeWarning: Boolean = false,
    val evidenceIntegrityOk: Boolean = true,
    val errorMessage: String? = null,
    val approved: Boolean = false
)

@HiltViewModel
class ResumePreviewViewModel @Inject constructor(
    private val applicationTracker: ApplicationTracker,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResumePreviewUiState())
    val uiState: StateFlow<ResumePreviewUiState> = _uiState.asStateFlow()

    fun load(appId: String) {
        viewModelScope.launch {
            _uiState.value = ResumePreviewUiState(isLoading = true)
            try {
                val app = applicationTracker.getApplication(appId)
                if (app == null) {
                    _uiState.value = ResumePreviewUiState(
                        isLoading = false,
                        errorMessage = "Application not found: $appId"
                    )
                    return@launch
                }

                val profile = profileRepository.getProfile() ?: CandidateProfile()

                // Build resume: use blank JdRequirement since JD text is not persisted with the
                // ApplicationRecord (only hash is stored). All confirmed facts appear; skill
                // ranking falls back to alphabetical. Documented in DECISIONS.md.
                val resume = try {
                    val jd = JdRequirement()
                    ResumeBuilder().build(profile, jd)
                } catch (e: Exception) {
                    _uiState.value = ResumePreviewUiState(
                        isLoading = false,
                        application = app,
                        errorMessage = "Cannot build resume: ${e.message}"
                    )
                    return@launch
                }

                val pdf = try {
                    PdfGenerator().generate(
                        resume = resume,
                        company = app.company,
                        role = app.role,
                        jdHash = app.jdHash,
                        version = app.resumeVersion
                    )
                } catch (e: Exception) {
                    _uiState.value = ResumePreviewUiState(
                        isLoading = false,
                        application = app,
                        errorMessage = "PDF generation failed: ${e.message}"
                    )
                    return@launch
                }

                _uiState.value = ResumePreviewUiState(
                    isLoading = false,
                    application = app,
                    resume = resume,
                    pdf = pdf,
                    materialChangeWarning = resume.isMaterialChangeFromMaster,
                    evidenceIntegrityOk = resume.verifyEvidenceIntegrity()
                )
            } catch (e: Exception) {
                _uiState.value = ResumePreviewUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error loading resume preview"
                )
            }
        }
    }

    fun approve() {
        _uiState.value = _uiState.value.copy(approved = true)
    }
}
