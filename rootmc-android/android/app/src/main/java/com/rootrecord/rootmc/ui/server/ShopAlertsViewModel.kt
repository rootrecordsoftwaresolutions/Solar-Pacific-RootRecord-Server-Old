package com.rootrecord.rootmc.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.data.repository.ShopPriceAlertRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopAlertsUiState(
    val loading: Boolean = true,
    val serverId: String = "rootmc",
    val alerts: List<ShopPriceAlertRow> = emptyList(),
    val error: String? = null,
    val newItemKey: String = "",
    val newThreshold: String = "",
    val newAlertType: String = "below",
    val createInFlight: Boolean = false,
    val createMessage: String? = null,
)

@HiltViewModel
class ShopAlertsViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopAlertsUiState())
    val uiState: StateFlow<ShopAlertsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
            val serverId = config?.serverId ?: "rootmc"
            serverRepository.fetchShopAlerts(serverId)
                .onSuccess { alerts ->
                    _uiState.update { it.copy(loading = false, serverId = serverId, alerts = alerts) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(loading = false, serverId = serverId, error = err.message ?: "Could not load alerts.")
                    }
                }
        }
    }

    fun setNewItemKey(value: String) {
        _uiState.update { it.copy(newItemKey = value.uppercase().take(64), createMessage = null) }
    }

    fun setNewThreshold(value: String) {
        _uiState.update {
            it.copy(newThreshold = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12), createMessage = null)
        }
    }

    fun setNewAlertType(value: String) {
        _uiState.update { it.copy(newAlertType = value, createMessage = null) }
    }

    fun createAlert() {
        val state = _uiState.value
        val itemKey = state.newItemKey.trim()
        val threshold = state.newThreshold.toDoubleOrNull()
        if (itemKey.isBlank()) {
            _uiState.update { it.copy(createMessage = "Enter an item key (e.g. DIAMOND).") }
            return
        }
        if (threshold == null || threshold <= 0) {
            _uiState.update { it.copy(createMessage = "Enter a positive G threshold.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(createInFlight = true, createMessage = null) }
            serverRepository.createShopAlert(state.serverId, itemKey, state.newAlertType, threshold)
                .onSuccess {
                    _uiState.update {
                        it.copy(createInFlight = false, newItemKey = "", newThreshold = "", createMessage = "Alert saved.")
                    }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(createInFlight = false, createMessage = err.message ?: "Could not save alert.")
                    }
                }
        }
    }

    fun deleteAlert(alertId: String) {
        viewModelScope.launch {
            serverRepository.deleteShopAlert(alertId)
                .onSuccess { refresh() }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message ?: "Delete failed.") }
                }
        }
    }
}
