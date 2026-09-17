package com.rootrecord.kilauea.alerts.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.repository.AiAnalysisCatalog
import com.rootrecord.kilauea.alerts.data.repository.AiAnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiAnalysisUiState(
    val loading: Boolean = false,
    val catalog: AiAnalysisCatalog? = null,
    val error: String? = null,
)

@HiltViewModel
class AiAnalysisViewModel @Inject constructor(
    private val repo: AiAnalysisRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiAnalysisUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val r = repo.latest()
            _state.update {
                it.copy(
                    loading = false,
                    catalog = r.getOrNull(),
                    error = r.exceptionOrNull()?.message,
                )
            }
        }
    }
}
