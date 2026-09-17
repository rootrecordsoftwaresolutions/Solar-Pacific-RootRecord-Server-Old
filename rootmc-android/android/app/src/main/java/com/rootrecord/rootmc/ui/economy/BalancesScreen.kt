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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.repository.CirculatingBalanceRow
import com.rootrecord.rootmc.data.repository.CirculatingBalancesTotals
import com.rootrecord.rootmc.data.repository.CirculatingNationRow
import com.rootrecord.rootmc.data.repository.CirculatingTownRow
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.ui.server.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancesScreen(
    onBack: () -> Unit,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf(
        stringResource(R.string.balances_tab_players),
        stringResource(R.string.balances_tab_towns),
        stringResource(R.string.balances_tab_nations),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.balances_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.balances_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                uiState.serverName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            uiState.report?.syncedAt?.let { synced ->
                Text(
                    stringResource(R.string.balances_synced, synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            val report = uiState.report ?: return@Column
            BalancesTotalsCard(report.totals)

            TabRow(selectedTabIndex = uiState.selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(label) },
                    )
                }
            }

            when (uiState.selectedTab) {
                0 -> PlayerBalancesSection(report.players)
                1 -> TownBalancesSection(report.towns)
                else -> NationBalancesSection(report.nations)
            }
        }
    }
}

@Composable
private fun BalancesTotalsCard(totals: CirculatingBalancesTotals) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.balances_totals_title), style = MaterialTheme.typography.titleMedium)
        SummaryRow(stringResource(R.string.balances_total_players), totals.playerNotesG, totals.playerCount)
        SummaryRow(stringResource(R.string.balances_total_towns), totals.townNotesG, totals.townCount)
        SummaryRow(stringResource(R.string.balances_total_nations), totals.nationNotesG, totals.nationCount)
        Text(
            stringResource(R.string.balances_total_circulating, formatGold(totals.circulatingNotesG)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SummaryRow(label: String, gold: Double, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "${formatGold(gold)} · $count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlayerBalancesSection(rows: List<CirculatingBalanceRow>) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            EmptyBalancesText()
        } else {
            rows.take(100).forEach { row ->
                BalanceRow(
                    rank = row.rank,
                    primary = row.displayName.ifBlank { row.minecraftUsername ?: "—" },
                    secondary = null,
                    gold = row.notesG,
                    earnings = null,
                )
            }
        }
    }
}

@Composable
private fun TownBalancesSection(rows: List<CirculatingTownRow>) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            EmptyBalancesText()
        } else {
            rows.take(100).forEach { row ->
                BalanceRow(
                    rank = row.rank,
                    primary = row.displayName,
                    secondary = listOfNotNull(
                        row.nationName?.let { stringResource(R.string.balances_nation_label, it) },
                        row.mayorName?.let { stringResource(R.string.balances_mayor_label, it) },
                    ).joinToString(" · ").ifBlank { null },
                    gold = row.bondedG,
                    earnings = row.bondEarningsG,
                )
            }
        }
    }
}

@Composable
private fun NationBalancesSection(rows: List<CirculatingNationRow>) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            EmptyBalancesText()
        } else {
            rows.take(100).forEach { row ->
                BalanceRow(
                    rank = row.rank,
                    primary = row.displayName,
                    secondary = listOfNotNull(
                        row.leaderName?.let { stringResource(R.string.balances_leader_label, it) },
                        if (row.townCount > 0) {
                            stringResource(R.string.balances_town_count, row.townCount)
                        } else {
                            null
                        },
                    ).joinToString(" · ").ifBlank { null },
                    gold = row.bondedG,
                    earnings = row.bondEarningsG,
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(
    rank: Int,
    primary: String,
    secondary: String?,
    gold: Double,
    earnings: Double?,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "#$rank $primary",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatGold(gold),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (earnings != null) {
                    Text(
                        stringResource(R.string.balances_earnings, formatGold(earnings)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        secondary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyBalancesText() {
    Text(
        stringResource(R.string.balances_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatGold(value: Double): String = "${formatCurrency(value)} G"
