package com.rootrecord.rootmc.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.data.repository.VaultOrderRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultUiState(
    val loading: Boolean = true,
    val serverId: String = "rootmc",
    val pending: List<VaultOrderRow> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
            val serverId = config?.serverId ?: "rootmc"
            serverRepository.fetchVaultOrders(serverId)
                .onSuccess { orders ->
                    _uiState.update {
                        it.copy(loading = false, serverId = serverId, pending = orders)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(loading = false, error = err.message ?: "Could not load vault.")
                    }
                }
        }
    }
}
