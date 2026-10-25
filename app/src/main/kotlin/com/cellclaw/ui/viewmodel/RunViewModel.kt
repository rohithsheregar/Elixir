package com.cellclaw.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.approval.ApprovalToken
import com.cellclaw.approval.SubmitOutcome
import com.cellclaw.automation.AutomationOrchestrator
import com.cellclaw.automation.AutomationStep
import com.cellclaw.automation.NodeHierarchyProvider
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
import java.io.File
import javax.inject.Inject

data class RunUiState(
    val isLoading: Boolean = true,
    val application: ApplicationRecord? = null,
    val company: String = "",
    val role: String = "",
    val sourceUrl: String = "",
    val step: AutomationStep = AutomationStep.Idle,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val manualHandoffActive: Boolean = false,
    val manualHandoffMessage: String = "",
    val errorMessage: String? = null,
    val confirmationReference: String? = null
)

@HiltViewModel
class RunViewModel @Inject constructor(
    private val tracker: ApplicationTracker,
    private val profileRepository: ProfileRepository,
    private val orchestrator: AutomationOrchestrator,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    companion object {
        const val KEY_APP_ID = "appId"
    }

    private val _uiState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()

    init {
        val appId: String? = savedStateHandle[KEY_APP_ID]
        if (!appId.isNullOrBlank()) {
            load(appId)
        }
        viewModelScope.launch {
            orchestrator.step.collect { step ->
                handleOrchestratorStep(step)
            }
        }
    }

    fun load(appId: String) {
        savedStateHandle[KEY_APP_ID] = appId
        viewModelScope.launch {
            val app = tracker.getApplication(appId)
            if (app == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Application not found: $appId"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                application = app,
                company = app.company,
                role = app.role,
                sourceUrl = app.sourceUrl.orEmpty(),
                errorMessage = null
            )
        }
    }

    private fun handleOrchestratorStep(step: AutomationStep) {
        when (step) {
            is AutomationStep.Paused -> {
                _uiState.value = _uiState.value.copy(
                    step = step,
                    isPaused = true,
                    pauseReason = step.reason
                )
            }
            is AutomationStep.Failed -> {
                _uiState.value = _uiState.value.copy(
                    step = step,
                    errorMessage = step.error
                )
            }
            is AutomationStep.Completed -> {
                _uiState.value = _uiState.value.copy(
                    step = step,
                    confirmationReference = step.confirmationReference,
                    isPaused = false
                )
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    step = step,
                    isPaused = false,
                    pauseReason = null
                )
            }
        }
    }

    fun startAutomation(
        token: ApprovalToken,
        resumeFile: File? = null,
        nodeProvider: NodeHierarchyProvider
    ) {
        val app = _uiState.value.application ?: return
        viewModelScope.launch {
            val profile = profileRepository.getProfile() ?: CandidateProfile()
            orchestrator.runAutomation(
                application = app,
                token = token,
                profile = profile,
                resumeFile = resumeFile,
                nodeProvider = nodeProvider
            )
        }
    }

    fun pause(reason: String = "User requested pause") {
        orchestrator.pause(reason)
        _uiState.value = _uiState.value.copy(
            isPaused = true,
            pauseReason = reason
        )
    }

    fun resume() {
        orchestrator.resumeSession()
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            pauseReason = null,
            manualHandoffActive = false
        )
    }

    fun triggerManualHandoff(message: String = "Manual intervention required (CAPTCHA, OTP, or Login)") {
        pause("Manual intervention: $message")
        _uiState.value = _uiState.value.copy(
            manualHandoffActive = true,
            manualHandoffMessage = message
        )
    }

    fun completeManualHandoff() {
        _uiState.value = _uiState.value.copy(
            manualHandoffActive = false,
            manualHandoffMessage = ""
        )
        resume()
    }

    fun confirmFinalSubmit(
        tapSubmitAction: suspend () -> Unit = {},
        fetchPostSubmitPageTexts: suspend () -> List<String> = { emptyList() }
    ) {
        viewModelScope.launch {
            val outcome = orchestrator.executeFinalSubmit(
                tapSubmitAction = tapSubmitAction,
                fetchPostSubmitPageTexts = fetchPostSubmitPageTexts
            )
            when (outcome) {
                is SubmitOutcome.Confirmed -> {
                    _uiState.value = _uiState.value.copy(
                        confirmationReference = outcome.reference
                    )
                }
                is SubmitOutcome.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = outcome.error
                    )
                }
                is SubmitOutcome.Rejected -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Submission rejected: ${outcome.reason}"
                    )
                }
                is SubmitOutcome.Unknown -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Submission outcome unknown: ${outcome.reason}"
                    )
                }
            }
        }
    }

    fun cancelAutomation(reason: String = "User cancelled automation") {
        val app = _uiState.value.application
        orchestrator.pause("Cancelled")
        if (app != null) {
            viewModelScope.launch {
                try {
                    val updated = tracker.transitionState(app.id, ApplicationState.CANCELLED, reason)
                    _uiState.value = _uiState.value.copy(
                        application = updated,
                        errorMessage = "Automation cancelled"
                    )
                } catch (_: Exception) {
                    // Ignore state transition errors on cancellation
                }
            }
        }
    }
}
