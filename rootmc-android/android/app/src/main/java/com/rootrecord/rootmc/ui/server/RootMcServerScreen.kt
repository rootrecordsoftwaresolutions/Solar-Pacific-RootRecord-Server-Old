package com.rootrecord.rootmc.ui.server

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.repository.FeaturedServerConfig
import com.rootrecord.rootmc.data.repository.McMMOStats
import com.rootrecord.rootmc.data.repository.NetWorthLeaderboardEntry
import com.rootrecord.rootmc.data.repository.NetWorthStats
import com.rootrecord.rootmc.data.repository.PlaytimeLeaderboardEntry
import com.rootrecord.rootmc.data.repository.PlaytimeStats
import com.rootrecord.rootmc.data.repository.RootShopsSnapshot
import com.rootrecord.rootmc.data.repository.ServerItemTotal
import com.rootrecord.rootmc.data.repository.ShopListingRow
import com.rootrecord.rootmc.data.repository.ShopPriceRow
import com.rootrecord.rootmc.ui.components.LiveMapWebView
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.MapUrlUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RootMcServerScreen(
    onAuth: () -> Unit,
    onOpenLeaderboards: () -> Unit,
    onOpenRealm: () -> Unit,
    onOpenStockMarket: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenShopAlerts: () -> Unit = {},
    onOpenMayorDashboard: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenDailyReport: (serverId: String, serverName: String) -> Unit = { _, _ -> },
    viewModel: RootMcServerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.nav_rootmc), style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(stringResource(R.string.server_refresh))
                    }
                }
                Text(
                    stringResource(R.string.server_lead),
                    style = MaterialTheme.typography.bodyMedium,
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

            uiState.worldAddedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            if (!uiState.signedIn) {
                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.server_sign_in_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.server_sign_in_lead))
                    Button(onClick = onAuth, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.sign_in))
                    }
                }
            }

            if (uiState.signedIn) {
                uiState.server?.let { server ->
                    PlayerDashboardCard(
                        username = uiState.minecraftUsername,
                        minecraftLinked = uiState.minecraftLinked,
                        netWorth = uiState.netWorth,
                        playtime = uiState.playtime,
                        mcmmo = uiState.mcmmo,
                        server = server,
                        marketStacks = uiState.marketStacks,
                        reserveBalance = uiState.reserveBalance,
                        onAuth = onAuth,
                    )
                }
            }

            uiState.server?.let { server ->
                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(server.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.server_address_label, server.address))
                    Text(stringResource(R.string.server_world_label, server.defaultWorldName))
                    Text(stringResource(R.string.server_version_label, server.gameVersion))
                    ServerStatusRow(server)
                    OutlinedButton(
                        onClick = {
                            onOpenDailyReport(server.serverId, server.name)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.daily_report_open))
                    }
                }

                if (uiState.signedIn && !uiState.minecraftLinked) {
                    AccountLinkBanner(verifyUrl = server.verifyUrl)
                }

                MapUrlUtils.normalizeMapUrl(server.mapUrl.orEmpty()).takeIf { it.isNotBlank() }?.let { mapUrl ->
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.server_map_title), style = MaterialTheme.typography.titleMedium)
                        LiveMapWebView(
                            mapUrl = mapUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(top = 8.dp),
                        )
                    }
                }

                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.playtime_section_title), style = MaterialTheme.typography.titleMedium)
                    when {
                        !uiState.signedIn -> Text(stringResource(R.string.playtime_sign_in_required))
                        !uiState.minecraftLinked -> Text(stringResource(R.string.stats_link_required_short))
                        uiState.playtime != null -> PlaytimeDetailPanel(uiState.playtime!!)
                        else -> Text(stringResource(R.string.playtime_pending_sync))
                    }
                    if (uiState.playtimeLeaderboard.isNotEmpty()) {
                        Text(
                            stringResource(R.string.playtime_leaderboard_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        uiState.playtimeLeaderboard.forEach { entry ->
                            PlaytimeLeaderboardRow(entry)
                        }
                    }
                }

                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.net_worth_section_title), style = MaterialTheme.typography.titleMedium)
                    when {
                        !uiState.signedIn -> Text(stringResource(R.string.net_worth_sign_in_required))
                        !uiState.minecraftLinked -> Text(stringResource(R.string.stats_link_required_short))
                        uiState.netWorth != null -> NetWorthDetailPanel(uiState.netWorth!!)
                        else -> Text(stringResource(R.string.net_worth_none_on_server))
                    }
                    if (uiState.netWorthLeaderboard.isNotEmpty()) {
                        Text(
                            stringResource(R.string.net_worth_leaderboard_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        uiState.netWorthLeaderboard.forEach { entry ->
                            NetWorthLeaderboardRow(entry)
                        }
                    }
                    if (uiState.serverItemTotals.isNotEmpty()) {
                        Text(
                            stringResource(R.string.server_items_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        uiState.serverItemTotals.forEach { item ->
                            Text(
                                if (item.avgPrice > 0) {
                                    stringResource(
                                        R.string.server_item_row_priced,
                                        formatItemLabel(item.itemKey),
                                        item.totalQuantity,
                                        formatMoney(item.avgPrice),
                                    )
                                } else {
                                    stringResource(
                                        R.string.server_item_row,
                                        formatItemLabel(item.itemKey),
                                        item.totalQuantity,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.root_shops_section_title), style = MaterialTheme.typography.titleMedium)
                    uiState.rootShops?.shareUrl?.let { shareUrl ->
                        OutlinedButton(
                            onClick = {
                                CustomTabsIntent.Builder().build()
                                    .launchUrl(context, shareUrl.toUri())
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Text(stringResource(R.string.root_shops_share))
                        }
                    }
                    RootShopsPanel(uiState.rootShops)
                }

                if (uiState.signedIn && uiState.minecraftLinked) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenStockMarket,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.stock_market_open))
                        }
                        OutlinedButton(
                            onClick = onOpenVault,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.vault_open))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenShopAlerts,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.shop_alerts_open))
                        }
                        if (uiState.isMayor) {
                            OutlinedButton(
                                onClick = onOpenMayorDashboard,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.mayor_dashboard_open))
                            }
                        }
                    }
                }

                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mcmmo_section_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.mcmmo_per_server_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    when {
                        !uiState.signedIn -> {
                            Text(stringResource(R.string.mcmmo_sign_in_required))
                            Button(onClick = onAuth, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.sign_in))
                            }
                        }
                        !uiState.minecraftLinked -> Text(stringResource(R.string.stats_link_required_short))
                        uiState.mcmmo != null -> {
                            McMMODetailPanel(mcmmo = uiState.mcmmo!!)
                        }
                        else -> {
                            Text(stringResource(R.string.mcmmo_none_on_server))
                            Text(
                                stringResource(R.string.mcmmo_sync_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (uiState.signedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenLeaderboards) {
                            Text(stringResource(R.string.server_open_leaderboards))
                        }
                        OutlinedButton(onClick = onOpenRealm) {
                            Text(stringResource(R.string.realm_title))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val url = server.realmUrl.ifBlank { "https://rootmc.net/" }
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.server_open_realm_web))
                    }
                }
            }
        }
    }

    if (onBack != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.server?.name ?: stringResource(R.string.nav_rootmc)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.server_refresh))
                        }
                    },
                )
            },
        ) { padding ->
            content(Modifier.padding(padding))
        }
    } else {
        content(Modifier)
    }
}

@Composable
private fun PlayerDashboardCard(
    username: String?,
    minecraftLinked: Boolean,
    netWorth: NetWorthStats?,
    playtime: PlaytimeStats?,
    mcmmo: McMMOStats?,
    server: FeaturedServerConfig,
    marketStacks: Int?,
    reserveBalance: Double?,
    onAuth: () -> Unit,
) {
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.player_dashboard_title), style = MaterialTheme.typography.titleMedium)

        if (!minecraftLinked || username.isNullOrBlank()) {
            Text(
                stringResource(R.string.player_dashboard_link_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(onClick = onAuth, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.sign_in))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = "https://minotar.net/helm/${username}/100.png",
                    contentDescription = stringResource(R.string.discord_linked_avatar_cd, username),
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(username, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            R.string.player_dashboard_wallet,
                            formatGoldG(netWorth?.balanceValue),
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        stringResource(R.string.player_dashboard_wallet_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerStatChip(
                    label = stringResource(R.string.player_dashboard_net_worth),
                    value = formatGoldG(netWorth?.totalValue),
                )
                netWorth?.rank?.let { rank ->
                    PlayerStatChip(
                        label = stringResource(R.string.player_dashboard_rank),
                        value = "#$rank",
                    )
                }
                playtime?.let {
                    PlayerStatChip(
                        label = stringResource(R.string.player_dashboard_playtime),
                        value = formatPlaytime(it.totalSeconds),
                    )
                }
                mcmmo?.let {
                    PlayerStatChip(
                        label = stringResource(R.string.player_dashboard_mcmmo),
                        value = "${it.powerLevel} PL",
                    )
                }
            }

            if (netWorth == null && playtime == null && mcmmo == null) {
                Text(
                    stringResource(R.string.player_dashboard_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Text(
            stringResource(R.string.player_dashboard_server_title),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 14.dp),
        )
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerStatChip(
                label = stringResource(R.string.player_dashboard_server_version),
                value = server.gameVersion,
            )
            PlayerStatChip(
                label = stringResource(R.string.player_dashboard_server_status),
                value = when {
                    server.rootmcPluginInstalled -> stringResource(R.string.player_dashboard_server_live)
                    server.connected -> stringResource(R.string.player_dashboard_server_partial)
                    else -> stringResource(R.string.player_dashboard_server_offline)
                },
            )
            marketStacks?.takeIf { it > 0 }?.let { stacks ->
                PlayerStatChip(
                    label = stringResource(R.string.player_dashboard_market),
                    value = stringResource(R.string.player_dashboard_market_stacks, stacks),
                )
            }
            reserveBalance?.takeIf { it > 0 }?.let { reserve ->
                PlayerStatChip(
                    label = stringResource(R.string.player_dashboard_reserve),
                    value = formatGoldG(reserve),
                )
            }
        }
    }
}

@Composable
private fun PlayerStatChip(label: String, value: String) {
    Column(
        modifier = Modifier.widthIn(min = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun formatGoldG(value: Double?): String =
    if (value == null || value <= 0.0) "—" else "${formatCurrency(value)} G"

@Composable
fun ServerStatusRow(server: FeaturedServerConfig) {
    when {
        server.rootmcPluginInstalled -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.server_plugins_online),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        server.connected -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.server_partial_online),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, contentDescription = null)
                Text(
                    stringResource(R.string.server_plugin_offline),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountLinkBanner(verifyUrl: String) {
    val context = LocalContext.current
    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_link_banner_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_link_banner_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Button(
            onClick = {
                CustomTabsIntent.Builder().build().launchUrl(context, verifyUrl.toUri())
            },
        ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Text(stringResource(R.string.server_open_verify))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun McMMODetailPanel(mcmmo: McMMOStats) {
    Text(
        stringResource(R.string.mcmmo_power_level, mcmmo.powerLevel),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    mcmmo.syncedAt?.let {
        Text(
            stringResource(R.string.mcmmo_last_synced, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        mcmmo.skills.entries
            .sortedByDescending { it.value }
            .forEach { (skill, level) ->
                MinecraftCard {
                    Text(formatSkillName(skill), style = MaterialTheme.typography.labelSmall)
                    Text("$level", style = MaterialTheme.typography.titleMedium)
                }
            }
    }
}

@Composable
private fun PlaytimeDetailPanel(playtime: PlaytimeStats) {
    Text(
        stringResource(R.string.playtime_total, formatPlaytime(playtime.totalSeconds)),
        style = MaterialTheme.typography.titleMedium,
    )
    playtime.firstJoinAt?.let {
        Text(
            stringResource(R.string.playtime_first_join, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    playtime.lastLoginAt?.let {
        Text(
            stringResource(R.string.playtime_last_login, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetWorthDetailPanel(netWorth: NetWorthStats) {
    netWorth.rank?.let {
        Text(
            stringResource(R.string.net_worth_rank, it),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Text(stringResource(R.string.net_worth_total, formatCurrency(netWorth.totalValue)))
    Text(
        stringResource(R.string.net_worth_balance, formatCurrency(netWorth.balanceValue)),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        stringResource(R.string.net_worth_inventory, formatCurrency(netWorth.inventoryValue)),
        style = MaterialTheme.typography.bodySmall,
    )
    netWorth.syncedAt?.let {
        Text(
            stringResource(R.string.net_worth_last_synced, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RootShopsPanel(snapshot: RootShopsSnapshot?) {
    if (snapshot == null || (snapshot.prices.isEmpty() && snapshot.listings.isEmpty())) {
        Text(
            stringResource(R.string.root_shops_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    snapshot.prices.take(8).forEach { row: ShopPriceRow ->
        Text(
            stringResource(
                R.string.root_shops_price_row,
                formatItemLabel(row.itemKey),
                formatMoney(row.avgPrice),
                row.sampleCount,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (snapshot.listings.isNotEmpty()) {
        Text(
            stringResource(R.string.root_shops_listings_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        snapshot.listings.take(6).forEach { row: ShopListingRow ->
            Text(
                stringResource(
                    R.string.root_shops_listing_row,
                    formatItemLabel(row.itemKey),
                    formatMoney(row.price),
                    row.ownerUsername ?: "?",
                    row.x,
                    row.y,
                    row.z,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlaytimeLeaderboardRow(entry: PlaytimeLeaderboardEntry) {
    val name = entry.minecraftUsername ?: "Player"
    Text(
        stringResource(
            R.string.playtime_leaderboard_row,
            entry.rank,
            name,
            formatPlaytime(entry.totalSeconds),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun NetWorthLeaderboardRow(entry: NetWorthLeaderboardEntry) {
    val name = entry.minecraftUsername ?: "Player"
    Text(
        stringResource(
            R.string.net_worth_leaderboard_row,
            entry.rank,
            name,
            formatCurrency(entry.totalValue),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

internal fun formatMoney(value: Double): String = formatCurrency(value)

internal fun formatCurrency(value: Double): String {
    if (value <= 0.0) return "0.000"
    return if (value >= 1_000_000) {
        String.format("%.3fM", value / 1_000_000)
    } else if (value >= 1_000) {
        String.format("%.3fK", value / 1_000)
    } else {
        String.format("%.3f", value)
    }
}

internal fun formatItemLabel(itemKey: String): String =
    itemKey.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

internal fun formatPlaytime(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0m"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

internal fun formatSkillName(key: String): String =
    key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
