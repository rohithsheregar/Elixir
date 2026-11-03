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
import com.cellclaw.resume.ResumeDiffResult
import com.cellclaw.resume.ResumeDiffer
import com.cellclaw.tracker.ApplicationPayload
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationTracker
import com.cellclaw.tracker.toJdRequirement
import kotlinx.serialization.json.Json
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ResumeViewTab {
    STRUCTURED,
    DIFF
}

data class ResumePreviewUiState(
    val isLoading: Boolean = true,
    val application: ApplicationRecord? = null,
    val resume: ResumeContent? = null,
    val masterResume: ResumeContent? = null,
    val diffResult: ResumeDiffResult? = null,
    val pdf: GeneratedPdf? = null,
    val materialChangeWarning: Boolean = false,
    val evidenceIntegrityOk: Boolean = true,
    val errorMessage: String? = null,
    val approved: Boolean = false,
    val activeTab: ResumeViewTab = ResumeViewTab.STRUCTURED
)

@HiltViewModel
class ResumePreviewViewModel @Inject constructor(
    private val applicationTracker: ApplicationTracker,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
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

                // Build resume: load persisted JD requirement from application notes for tailored ranking
                val jd = try {
                    app.notes?.let { noteStr ->
                        try {
                            json.decodeFromString<ApplicationPayload>(noteStr).jdRequirement?.toJdRequirement()
                        } catch (_: Exception) {
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                } ?: JdRequirement()

                val resume = try {
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

                val masterResume = try {
                    ResumeBuilder().build(profile, JdRequirement())
                } catch (_: Exception) {
                    null
                }

                val diffResult = if (masterResume != null) {
                    ResumeDiffer().diff(masterResume, resume)
                } else null

                _uiState.value = ResumePreviewUiState(
                    isLoading = false,
                    application = app,
                    resume = resume,
                    masterResume = masterResume,
                    diffResult = diffResult,
                    pdf = pdf,
                    materialChangeWarning = diffResult?.isMaterialChange ?: resume.isMaterialChangeFromMaster,
                    evidenceIntegrityOk = resume.verifyEvidenceIntegrity(),
                    activeTab = ResumeViewTab.STRUCTURED
                )
            } catch (e: Exception) {
                _uiState.value = ResumePreviewUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error loading resume preview"
                )
            }
        }
    }

    fun setTab(tab: ResumeViewTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun approve() {
        _uiState.value = _uiState.value.copy(approved = true)
    }
}
