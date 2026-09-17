package com.rootrecord.rootmc.ui.economy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.rootrecord.rootmc.data.repository.TreasuryReserveSnapshot
import com.rootrecord.rootmc.ui.charts.FlowBarChart
import com.rootrecord.rootmc.ui.charts.GoldLineChart
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.ui.server.formatCurrency
import com.rootrecord.rootmc.ui.server.formatItemLabel
import com.rootrecord.rootmc.ui.server.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EconomyScreen(
    onOpenBalances: () -> Unit = {},
    viewModel: EconomyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.economy_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.economy_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MinecraftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenBalances) {
                Text(stringResource(R.string.balances_open), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.balances_open_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            val overview = uiState.overview ?: return@Column
            val reserve = overview.reserve

            EconomyHeroRow(reserve, overview.marketSummary.totalServerItems)

            EconomyMtdRow(reserve)

            MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.economy_reserve_chart_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                val labels = viewModel.reserveChartLabels()
                val values = viewModel.reserveChartValues()
                if (values.isEmpty()) {
                    Text(
                        stringResource(R.string.economy_chart_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    GoldLineChart(
                        labels = labels,
                        values = values,
                        secondaryValues = viewModel.holderChartValues(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.economy_reserve_chart_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.economy_flow_chart_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                val flowLabels = viewModel.flowChartLabels()
                if (flowLabels.isEmpty()) {
                    Text(
                        stringResource(R.string.economy_chart_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    FlowBarChart(
                        labels = flowLabels,
                        inflows = viewModel.flowInflows(),
                        outflows = viewModel.flowOutflows(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.economy_flow_chart_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            reserve.noteSupplySummary?.let { summary ->
                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.economy_note_supply_title), style = MaterialTheme.typography.titleSmall)
                    Text(summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    reserve.backingPct?.let { pct ->
                        Text(
                            stringResource(R.string.economy_backing_pct, pct),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (overview.marketMovers.isNotEmpty()) {
                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.economy_movers_title), style = MaterialTheme.typography.titleSmall)
                    overview.marketMovers.forEach { mover ->
                        MarketMoverRow(mover)
                    }
                }
            }

            EconomyLeaderboardSection(
                title = stringResource(R.string.net_worth_leaderboard_title),
                rows = overview.netWorthTop.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    "#${entry.rank} $name — ${formatCurrency(entry.totalValue)} G"
                },
            )
            EconomyLeaderboardSection(
                title = stringResource(R.string.leaderboards_gold_mint_title),
                rows = overview.mintTop.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    "#${entry.rank} $name — ${formatCurrency(entry.grossMintedG)} G"
                },
            )
            EconomyLeaderboardSection(
                title = stringResource(R.string.leaderboards_gold_found_title),
                rows = overview.goldFoundTop.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    "#${entry.rank} $name — ${formatCurrency(entry.minedSinceJulyG)} G"
                },
            )
        }
    }
}

@Composable
private fun EconomyHeroRow(reserve: TreasuryReserveSnapshot, marketStacks: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroStat(
            label = stringResource(R.string.economy_hero_wallet),
            value = formatGold(reserve.playerNotesG),
            modifier = Modifier.weight(1f),
        )
        HeroStat(
            label = stringResource(R.string.economy_hero_reserve),
            value = formatGold(reserve.balance),
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroStat(
            label = stringResource(R.string.economy_hero_found),
            value = formatGold(reserve.goldFoundSinceJuly),
            modifier = Modifier.weight(1f),
        )
        HeroStat(
            label = stringResource(R.string.economy_hero_market),
            value = marketStacks.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EconomyMtdRow(reserve: TreasuryReserveSnapshot) {
    val monthLabel = reserve.viewMonth ?: stringResource(R.string.economy_mtd_default)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroStat(
            label = stringResource(R.string.economy_mtd_in, monthLabel),
            value = formatGold(reserve.monthInflow),
            modifier = Modifier.weight(1f),
        )
        HeroStat(
            label = stringResource(R.string.economy_mtd_out, monthLabel),
            value = formatGold(reserve.monthOutflow),
            modifier = Modifier.weight(1f),
        )
        HeroStat(
            label = stringResource(R.string.economy_mtd_net, monthLabel),
            value = formatGold(reserve.monthNet),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    MinecraftCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MarketMoverRow(row: StockMarketItemRow) {
    val change = row.change24hPct
    val sign = if (change != null && change > 0) "+" else ""
    val changeText = change?.let { "$sign${"%.1f".format(it)}%" } ?: "—"
    Text(
        "${formatItemLabel(row.itemKey)} · ${formatMoney(row.displayPrice())} G · $changeText",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun EconomyLeaderboardSection(title: String, rows: List<String>) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.leaderboards_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            rows.forEach { row ->
                Text(row, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun formatGold(value: Double?): String =
    if (value == null || !value.isFinite()) "—" else "${formatCurrency(value)} G"
