package com.cellclaw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.agent.JobResearchEngine
import com.cellclaw.agent.ResearchEngineState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResearchViewModel @Inject constructor(
    private val researchEngine: JobResearchEngine
) : ViewModel() {

    val state: StateFlow<ResearchEngineState> = researchEngine.state

    fun startResearch(query: String? = null) {
        viewModelScope.launch {
            researchEngine.runResearch(query)
        }
    }
}

