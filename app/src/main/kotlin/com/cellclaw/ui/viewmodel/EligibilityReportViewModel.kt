package com.cellclaw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.eligibility.EligibilityReport
import com.cellclaw.eligibility.EligibilityResult
import com.cellclaw.eligibility.EligibilityStatus
import com.cellclaw.eligibility.OverallEligibility
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class EligibilityReportUiState(
    val isLoading: Boolean = true,
    val application: ApplicationRecord? = null,
    val report: EligibilityReport? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class EligibilityReportViewModel @Inject constructor(
    private val applicationTracker: ApplicationTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(EligibilityReportUiState())
    val uiState: StateFlow<EligibilityReportUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun load(appId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val app = applicationTracker.getApplication(appId)
                if (app == null) {
                    _uiState.value = EligibilityReportUiState(
                        isLoading = false,
                        errorMessage = "Application not found: $appId"
                    )
                } else {
                    val report = try {
                        app.notes?.let { json.decodeFromString<EligibilityReport>(it) }
                    } catch (_: Exception) {
                        null
                    } ?: run {
                        val overall = try {
                            OverallEligibility.valueOf(app.eligibilityResult)
                        } catch (_: Exception) {
                            OverallEligibility.UNKNOWN
                        }
                        EligibilityReport(
                            overall = overall,
                            criteria = listOf(
                                EligibilityResult(
                                    criterion = "Overall Eligibility",
                                    status = when (overall) {
                                        OverallEligibility.ELIGIBLE -> EligibilityStatus.PASS
                                        OverallEligibility.NOT_ELIGIBLE -> EligibilityStatus.FAIL
                                        OverallEligibility.UNKNOWN -> EligibilityStatus.UNKNOWN
                                    },
                                    reason = "Status recorded: ${app.eligibilityResult}"
                                )
                            )
                        )
                    }

                    _uiState.value = EligibilityReportUiState(
                        isLoading = false,
                        application = app,
                        report = report
                    )
                }
            } catch (e: Exception) {
                _uiState.value = EligibilityReportUiState(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load eligibility report"
                )
            }
        }
    }
}
