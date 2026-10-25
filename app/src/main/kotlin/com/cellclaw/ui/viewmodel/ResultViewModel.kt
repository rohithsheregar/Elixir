package com.cellclaw.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val isLoading: Boolean = true,
    val applicationId: String = "",
    val company: String = "",
    val role: String = "",
    val state: ApplicationState = ApplicationState.DRAFT,
    val confirmationReference: String? = null,
    val sourceUrl: String? = null,
    val resumeVersion: Int = 1,
    val resumeHash: String? = null,
    val notes: String? = null,
    val updatedAt: Long = 0L,
    val manualReferenceInput: String = "",
    val errorMessage: String? = null,
    val actionSuccessMessage: String? = null
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val tracker: ApplicationTracker,
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    companion object {
        const val KEY_APP_ID = "appId"
    }

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        val appId: String? = savedStateHandle[KEY_APP_ID]
        if (!appId.isNullOrBlank()) {
            load(appId)
        }
    }

    fun load(appId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val app = tracker.getApplication(appId)
            if (app != null) {
                _uiState.value = ResultUiState(
                    isLoading = false,
                    applicationId = app.id,
                    company = app.company,
                    role = app.role,
                    state = app.state,
                    confirmationReference = app.confirmationReference,
                    sourceUrl = app.sourceUrl,
                    resumeVersion = app.resumeVersion,
                    resumeHash = app.resumeHash,
                    notes = app.notes,
                    updatedAt = app.updatedAt
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Application not found: $appId"
                )
            }
        }
    }

    fun updateManualReferenceInput(input: String) {
        _uiState.value = _uiState.value.copy(manualReferenceInput = input)
    }

    fun confirmSubmissionManually(reference: String? = null) {
        val current = _uiState.value
        val resolvedRef = (reference ?: current.manualReferenceInput).trim().ifEmpty {
            "MANUAL-REF-${System.currentTimeMillis().toString().takeLast(6)}"
        }

        viewModelScope.launch {
            try {
                val updated = tracker.transitionState(
                    id = current.applicationId,
                    targetState = ApplicationState.SUBMITTED_CONFIRMED,
                    notes = "Manually confirmed by user",
                    confirmationReference = resolvedRef
                )
                _uiState.value = _uiState.value.copy(
                    state = updated.state,
                    confirmationReference = updated.confirmationReference,
                    actionSuccessMessage = "Application marked as SUBMITTED_CONFIRMED"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to confirm submission: ${e.message}"
                )
            }
        }
    }

    fun retrySubmission(onReadyForReview: (String) -> Unit = {}) {
        val current = _uiState.value
        viewModelScope.launch {
            try {
                if (current.state == ApplicationState.SUBMISSION_UNKNOWN) {
                    tracker.transitionState(
                        id = current.applicationId,
                        targetState = ApplicationState.FAILED,
                        notes = "User requested retry after unknown submission state"
                    )
                }
                val draft = tracker.transitionState(
                    id = current.applicationId,
                    targetState = ApplicationState.DRAFT,
                    notes = "Reset to DRAFT for resubmission retry"
                )
                val ready = tracker.transitionState(
                    id = draft.id,
                    targetState = ApplicationState.READY_FOR_REVIEW
                )
                _uiState.value = _uiState.value.copy(
                    state = ready.state,
                    actionSuccessMessage = "Application reset to READY_FOR_REVIEW"
                )
                onReadyForReview(ready.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to retry application: ${e.message}"
                )
            }
        }
    }

    fun markAsFailed(reason: String) {
        val current = _uiState.value
        viewModelScope.launch {
            try {
                val updated = tracker.transitionState(
                    id = current.applicationId,
                    targetState = ApplicationState.FAILED,
                    notes = reason
                )
                _uiState.value = _uiState.value.copy(
                    state = updated.state,
                    actionSuccessMessage = "Application marked as FAILED"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to mark as failed: ${e.message}"
                )
            }
        }
    }
}
