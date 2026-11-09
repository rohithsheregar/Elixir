package com.cellclaw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.config.AppConfig
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackerUiState(
    val applications: List<ApplicationRecord> = emptyList(),
    val selectedFilter: ApplicationState? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val countsByState: Map<ApplicationState, Int> = emptyMap()
)

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val applicationTracker: ApplicationTracker,
    private val appConfig: AppConfig
) : ViewModel() {

    val themeMode: StateFlow<String> = appConfig.themeModeFlow

    fun toggleTheme() {
        val next = when (appConfig.themeMode) {
            "DARK" -> "LIGHT"
            "LIGHT" -> "SYSTEM"
            else -> "DARK"
        }
        appConfig.themeMode = next
    }

    private val _selectedFilter = MutableStateFlow<ApplicationState?>(null)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<TrackerUiState> = combine(
        applicationTracker.observeApplications(),
        _selectedFilter,
        _searchQuery
    ) { allApps, filter, query ->
        val counts = allApps.groupingBy { it.state }.eachCount()
        val filtered = allApps.filter { app ->
            val matchesFilter = filter == null || app.state == filter
            val matchesQuery = query.isBlank() ||
                app.company.contains(query, ignoreCase = true) ||
                app.role.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
        TrackerUiState(
            applications = filtered,
            selectedFilter = filter,
            searchQuery = query,
            isLoading = false,
            countsByState = counts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrackerUiState(isLoading = true)
    )

    fun setFilter(filter: ApplicationState?) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteApplication(id: String) {
        viewModelScope.launch {
            applicationTracker.deleteApplication(id)
        }
    }
}
