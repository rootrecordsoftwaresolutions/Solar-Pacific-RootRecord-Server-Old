package com.rootrecord.kilauea.alerts.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.repository.NwsAlertsRepository
import com.rootrecord.kilauea.alerts.data.repository.UsgVolcanoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class AlertsUiState(
    val loading: Boolean = false,
    val volcano: JsonObject? = null,
    val nws: JsonObject? = null,
    val error: String? = null,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val volcanoRepo: UsgVolcanoRepository,
    private val nwsRepo: NwsAlertsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlertsUiState())
    val state = _state.asStateFlow()

    init {
        load(false)
    }

    fun load(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val v = volcanoRepo.offlineFirst(force)
            val n = nwsRepo.offlineFirst(force)
            _state.update {
                it.copy(
                    loading = false,
                    volcano = v.getOrNull(),
                    nws = n.getOrNull(),
                    error = listOfNotNull(v.exceptionOrNull()?.message, n.exceptionOrNull()?.message)
                        .firstOrNull(),
                )
            }
        }
    }
}
