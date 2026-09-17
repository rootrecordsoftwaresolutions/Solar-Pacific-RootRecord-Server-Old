package com.rootrecord.rootmc.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.repository.StockMarketItemRow
import com.rootrecord.rootmc.ui.charts.GoldLineChart
import com.rootrecord.rootmc.ui.charts.MarketChartPeriod
import com.rootrecord.rootmc.ui.components.MinecraftCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StockMarketScreen(
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    onOpenVault: () -> Unit,
    onOpenShopAlerts: () -> Unit = {},
    viewModel: StockMarketViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.buyItemKey != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBuyDialog() },
            title = { Text(stringResource(R.string.stock_market_buy_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiState.buyItemKey.orEmpty())
                    OutlinedTextField(
                        value = uiState.buyQuantity,
                        onValueChange = viewModel::setBuyQuantity,
                        label = { Text(stringResource(R.string.stock_market_quantity)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.buyMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmBuy,
                    enabled = !uiState.buyInFlight,
                ) {
                    Text(stringResource(R.string.stock_market_buy_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBuyDialog) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stock_market_title)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenShopAlerts) {
                        Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.shop_alerts_open))
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.server_refresh))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.stock_market_lead),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenVault, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.vault_open))
                    }
                    uiState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            val selectedKey = uiState.selectedItemKey
            if (selectedKey != null) {
                item {
                    MarketChartCard(
                        itemKey = selectedKey,
                        historyLoading = uiState.historyLoading,
                        chartPeriod = uiState.chartPeriod,
                        labels = viewModel.chartLabels(),
                        values = viewModel.chartValues(),
                        onPeriodSelected = viewModel::setChartPeriod,
                    )
                }
            }

            if (uiState.items.isEmpty()) {
                item {
                    Text(stringResource(R.string.stock_market_empty))
                }
            } else {
                items(uiState.items, key = { it.itemKey }) { row ->
                    val selected = row.itemKey == selectedKey
                    MarketItemRow(
                        row = row,
                        selected = selected,
                        onSelect = { viewModel.selectItem(row.itemKey) },
                        onBuy = { viewModel.openBuyDialog(row.itemKey) },
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketChartCard(
    itemKey: String,
    historyLoading: Boolean,
    chartPeriod: MarketChartPeriod,
    labels: List<String>,
    values: List<Double>,
    onPeriodSelected: (MarketChartPeriod) -> Unit,
) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.stock_market_chart_title, formatItemLabel(itemKey)),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketChartPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = chartPeriod == period,
                        onClick = { onPeriodSelected(period) },
                        label = {
                            Text(
                                when (period) {
                                    MarketChartPeriod.HOUR -> stringResource(R.string.stock_market_period_hour)
                                    MarketChartPeriod.DAY -> stringResource(R.string.stock_market_period_day)
                                    MarketChartPeriod.WEEK -> stringResource(R.string.stock_market_period_week)
                                },
                            )
                        },
                    )
                }
            }
            when {
                historyLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                values.isEmpty() -> {
                    Text(
                        stringResource(R.string.stock_market_chart_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    GoldLineChart(labels = labels, values = values)
                }
            }
        }
    }
}

@Composable
private fun MarketItemRow(
    row: StockMarketItemRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onBuy: () -> Unit,
) {
    MinecraftCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
    ) {
        if (selected) {
            Text(
                stringResource(R.string.stock_market_selected),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatItemLabel(row.itemKey), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.stock_market_item_row,
                        formatMoney(row.displayPrice()),
                        row.totalQuantity,
                        row.shopCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                row.change24hPct?.let { change ->
                    val sign = if (change > 0) "+" else ""
                    val color = when {
                        change > 0.05 -> MaterialTheme.colorScheme.primary
                        change < -0.05 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        stringResource(R.string.stock_market_change_24h, "$sign${"%.1f".format(change)}%"),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
            OutlinedButton(onClick = onBuy) {
                Text(stringResource(R.string.stock_market_buy))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.server_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.vault_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.pending.isEmpty()) {
                Text(stringResource(R.string.vault_empty))
            } else {
                uiState.pending.forEach { order ->
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Text(formatItemLabel(order.itemKey), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                R.string.vault_order_row,
                                order.quantity,
                                formatMoney(order.pricePaid),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
