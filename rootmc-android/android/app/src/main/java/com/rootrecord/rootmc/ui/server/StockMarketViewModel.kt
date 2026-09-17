package com.rootrecord.rootmc.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.data.repository.StockMarketItemRow
import com.rootrecord.rootmc.data.repository.StockPricePoint
import com.rootrecord.rootmc.ui.charts.ChartBuckets
import com.rootrecord.rootmc.ui.charts.MarketChartPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockMarketUiState(
    val loading: Boolean = true,
    val serverId: String = "rootmc",
    val items: List<StockMarketItemRow> = emptyList(),
    val totalItems: Int = 0,
    val error: String? = null,
    val selectedItemKey: String? = null,
    val historyLoading: Boolean = false,
    val historyPoints: List<StockPricePoint> = emptyList(),
    val chartPeriod: MarketChartPeriod = MarketChartPeriod.HOUR,
    val buyItemKey: String? = null,
    val buyQuantity: String = "1",
    val buyInFlight: Boolean = false,
    val buyMessage: String? = null,
)

@HiltViewModel
class StockMarketViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockMarketUiState())
    val uiState: StateFlow<StockMarketUiState> = _uiState.asStateFlow()
    private var historyJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
            val serverId = config?.serverId ?: "rootmc"
            val page = serverRepository.fetchStockMarketItems(serverId, perPage = 50, inStockOnly = false)
                .getOrElse { return@launch _uiState.update { s ->
                    s.copy(loading = false, serverId = serverId, error = it.message ?: "Could not load market.")
                } }
            val selected = _uiState.value.selectedItemKey?.takeIf { key ->
                page.items.any { it.itemKey == key }
            } ?: page.items.firstOrNull()?.itemKey
            _uiState.update {
                it.copy(
                    loading = false,
                    serverId = serverId,
                    items = page.items,
                    totalItems = page.total,
                    selectedItemKey = selected,
                )
            }
            selected?.let { selectItem(it) }
        }
    }

    fun selectItem(itemKey: String) {
        _uiState.update { it.copy(selectedItemKey = itemKey) }
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _uiState.update { it.copy(historyLoading = true) }
            val points = serverRepository.fetchStockMarketHistory(_uiState.value.serverId, itemKey)
                .getOrElse { emptyList() }
            _uiState.update { it.copy(historyLoading = false, historyPoints = points) }
        }
    }

    fun setChartPeriod(period: MarketChartPeriod) {
        _uiState.update { it.copy(chartPeriod = period) }
    }

    fun chartLabels(): List<String> {
        val buckets = ChartBuckets.marketBuckets(_uiState.value.historyPoints, _uiState.value.chartPeriod)
        return buckets.map { ChartBuckets.formatMarketLabel(it.key, _uiState.value.chartPeriod) }
    }

    fun chartValues(): List<Double> = ChartBuckets
        .marketBuckets(_uiState.value.historyPoints, _uiState.value.chartPeriod)
        .map { it.close }

    fun openBuyDialog(itemKey: String) {
        _uiState.update { it.copy(buyItemKey = itemKey, buyQuantity = "1", buyMessage = null) }
    }

    fun dismissBuyDialog() {
        _uiState.update { it.copy(buyItemKey = null, buyInFlight = false, buyMessage = null) }
    }

    fun setBuyQuantity(value: String) {
        _uiState.update { it.copy(buyQuantity = value.filter { ch -> ch.isDigit() }.take(4)) }
    }

    fun confirmBuy() {
        val state = _uiState.value
        val itemKey = state.buyItemKey ?: return
        val qty = state.buyQuantity.toIntOrNull()?.coerceIn(1, 576) ?: 1
        viewModelScope.launch {
            _uiState.update { it.copy(buyInFlight = true, buyMessage = null) }
            serverRepository.buyFromApp(state.serverId, itemKey, qty)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            buyInFlight = false,
                            buyMessage = result.message ?: "Queued — claim with /vault in-game.",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(buyInFlight = false, buyMessage = err.message ?: "Purchase failed.")
                    }
                }
        }
    }
}
