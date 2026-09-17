package com.rootrecord.rootmc.ui.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.DailyReportPage
import com.rootrecord.rootmc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyReportUiState(
    val serverName: String = "",
    val page: DailyReportPage? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle.get<String>("serverId"))

    private val _uiState = MutableStateFlow(DailyReportUiState())
    val uiState: StateFlow<DailyReportUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<String>("serverName")?.let { name ->
            _uiState.update { it.copy(serverName = name) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            serverRepository.fetchDailyReports(serverId)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(loading = false, page = page, error = null)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(loading = false, error = err.message ?: "Could not load daily report.")
                    }
                }
        }
    }
}
