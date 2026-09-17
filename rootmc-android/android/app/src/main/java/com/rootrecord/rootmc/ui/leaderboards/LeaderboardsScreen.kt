package com.rootrecord.rootmc.ui.leaderboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.rootrecord.rootmc.data.repository.NetWorthLeaderboardEntry
import com.rootrecord.rootmc.data.repository.PlaytimeLeaderboardEntry
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.ui.server.formatCurrency
import com.rootrecord.rootmc.ui.server.formatPlaytime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardsScreen(
    viewModel: LeaderboardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.leaderboards_title)) },
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
                stringResource(R.string.leaderboards_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                uiState.serverName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            LeaderboardSection(
                title = stringResource(R.string.net_worth_leaderboard_title),
                rows = uiState.netWorth.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    stringResource(
                        R.string.net_worth_leaderboard_row,
                        entry.rank,
                        name,
                        formatCurrency(entry.totalValue),
                    )
                },
            )
            LeaderboardSection(
                title = stringResource(R.string.playtime_leaderboard_title),
                rows = uiState.playtime.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    stringResource(
                        R.string.playtime_leaderboard_row,
                        entry.rank,
                        name,
                        formatPlaytime(entry.totalSeconds),
                    )
                },
            )
            LeaderboardSection(
                title = stringResource(R.string.leaderboards_mcmmo_title),
                rows = uiState.mcmmo.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    "#${entry.rank} $name — ${entry.powerLevel} PL"
                },
            )
            LeaderboardSection(
                title = stringResource(R.string.leaderboards_gold_mint_title),
                rows = uiState.goldMinted.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    "#${entry.rank} $name — ${formatCurrency(entry.grossMintedG)} G"
                },
            )
            LeaderboardSection(
                title = stringResource(R.string.leaderboards_gold_found_title),
                rows = uiState.goldFound.map { entry ->
                    val name = entry.minecraftUsername ?: "Player"
                    "#${entry.rank} $name — ${formatCurrency(entry.minedSinceJulyG)} G"
                },
            )
        }
    }
}

@Composable
private fun LeaderboardSection(title: String, rows: List<String>) {
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
                Text(
                    row,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
