package com.rootrecord.rootmc.ui.server

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.ui.components.MinecraftCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopAlertsScreen(
    onBack: () -> Unit,
    viewModel: ShopAlertsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var typeExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shop_alerts_title)) },
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
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.shop_alerts_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shop_alerts_new), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = uiState.newItemKey,
                        onValueChange = viewModel::setNewItemKey,
                        label = { Text(stringResource(R.string.shop_alerts_item_key)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = uiState.newThreshold,
                        onValueChange = viewModel::setNewThreshold,
                        label = { Text(stringResource(R.string.shop_alerts_threshold)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = if (uiState.newAlertType == "above") {
                                stringResource(R.string.shop_alerts_type_above)
                            } else {
                                stringResource(R.string.shop_alerts_type_below)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.shop_alerts_type)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shop_alerts_type_below)) },
                                onClick = {
                                    viewModel.setNewAlertType("below")
                                    typeExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shop_alerts_type_above)) },
                                onClick = {
                                    viewModel.setNewAlertType("above")
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::createAlert,
                        enabled = !uiState.createInFlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.shop_alerts_save))
                    }
                    uiState.createMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            uiState.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
            if (uiState.alerts.isEmpty()) {
                item {
                    Text(stringResource(R.string.shop_alerts_empty))
                }
            } else {
                items(uiState.alerts, key = { it.id }) { alert ->
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(alert.itemKey.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                                val typeLabel = if (alert.alertType == "above") {
                                    stringResource(R.string.shop_alerts_type_above)
                                } else {
                                    stringResource(R.string.shop_alerts_type_below)
                                }
                                Text(
                                    stringResource(
                                        R.string.shop_alerts_row,
                                        typeLabel,
                                        alert.thresholdValue,
                                        alert.lastSeenPrice ?: 0.0,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                alert.lastNotifiedAt?.let {
                                    Text(
                                        stringResource(R.string.shop_alerts_notified, it.take(16)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteAlert(alert.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MayorDashboardScreen(
    onBack: () -> Unit,
    viewModel: MayorDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mayor_dashboard_title)) },
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
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                !uiState.signedIn -> Text(stringResource(R.string.server_sign_in_lead))
                !uiState.minecraftLinked -> Text(stringResource(R.string.server_link_required))
                !uiState.isMayor -> Text(stringResource(R.string.mayor_dashboard_not_mayor))
                else -> {
                    val town = uiState.town ?: return@Column
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Text(town.townName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.mayor_dashboard_residents, town.residentCount))
                        town.nationName?.let {
                            Text(stringResource(R.string.mayor_dashboard_nation, it))
                        }
                        if (town.isCapital) {
                            Text(stringResource(R.string.mayor_dashboard_capital))
                        }
                        town.syncedAt?.let {
                            Text(
                                stringResource(R.string.mayor_dashboard_synced, it.take(19)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        town.discordInviteUrl?.let { url ->
                            Button(
                                onClick = {
                                    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) {
                                Text(stringResource(R.string.mayor_dashboard_discord))
                            }
                        }
                    }
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.mayor_dashboard_shops, town.shopListingCount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (town.shopListings.isEmpty()) {
                            Text(stringResource(R.string.mayor_dashboard_no_shops))
                        } else {
                            town.shopListings.forEach { listing ->
                                Text(
                                    "${listing.itemKey.replace('_', ' ')} — ${listing.price} G @ ${listing.x}, ${listing.z}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
