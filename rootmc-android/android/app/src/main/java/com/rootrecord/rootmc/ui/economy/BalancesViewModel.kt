package com.rootrecord.rootmc.ui.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.CirculatingBalancesReport
import com.rootrecord.rootmc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BalancesUiState(
    val loading: Boolean = true,
    val report: CirculatingBalancesReport? = null,
    val serverName: String = "RootMC",
    val error: String? = null,
    val selectedTab: Int = 0,
)

@HiltViewModel
class BalancesViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BalancesUiState())
    val uiState: StateFlow<BalancesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
            val serverId = config?.serverId ?: "rootmc"
            val serverName = config?.name ?: "RootMC"
            serverRepository.fetchCirculatingBalances(serverId)
                .onSuccess { report ->
                    _uiState.update {
                        it.copy(loading = false, report = report, serverName = serverName)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            serverName = serverName,
                            error = err.message ?: "Could not load balances.",
                        )
                    }
                }
        }
    }
}
