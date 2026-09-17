package com.rootrecord.rootmc.ui.economy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.EconomyOverview
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.ui.charts.ChartBuckets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EconomyUiState(
    val loading: Boolean = true,
    val overview: EconomyOverview? = null,
    val error: String? = null,
)

@HiltViewModel
class EconomyViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EconomyUiState())
    val uiState: StateFlow<EconomyUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val serverId = serverRepository.fetchFeaturedServerConfig().getOrNull()?.serverId ?: "rootmc"
            serverRepository.fetchEconomyOverview(serverId)
                .onSuccess { overview ->
                    _uiState.update { it.copy(loading = false, overview = overview) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(loading = false, error = err.message ?: "Could not load economy.")
                    }
                }
        }
    }

    fun reserveChartLabels(): List<String> {
        val points = ChartBuckets.reserveDailyLabels(
            _uiState.value.overview?.reserve?.balanceHistory.orEmpty(),
        )
        return points.map { it.label.takeLast(5) }
    }

    fun reserveChartValues(): List<Double> =
        ChartBuckets.reserveDailyLabels(_uiState.value.overview?.reserve?.balanceHistory.orEmpty())
            .map { it.value }

    fun holderChartValues(): List<Double?>? {
        val reserve = ChartBuckets.reserveDailyLabels(
            _uiState.value.overview?.reserve?.balanceHistory.orEmpty(),
        )
        val reserveValues = reserve.map { it.value }
        val holderByDay = _uiState.value.overview?.reserve?.holderSupplyDaily
            ?.associate { it.label to it.value }
            .orEmpty()
        val holderValues = reserve.map { holderByDay[it.label] }
        val maxReserve = reserveValues.maxOrNull() ?: 0.0
        val maxHolder = holderValues.filterNotNull().maxOrNull() ?: 0.0
        if (maxHolder > maxReserve * 1.15) return null
        return holderValues
    }

    fun flowChartLabels(): List<String> =
        _uiState.value.overview?.reserve?.flowDaily?.takeLast(21)?.map { it.day.takeLast(5) }.orEmpty()

    fun flowInflows(): List<Double> =
        _uiState.value.overview?.reserve?.flowDaily?.takeLast(21)?.map { it.inflow }.orEmpty()

    fun flowOutflows(): List<Double> =
        _uiState.value.overview?.reserve?.flowDaily?.takeLast(21)?.map { it.outflow }.orEmpty()
}
