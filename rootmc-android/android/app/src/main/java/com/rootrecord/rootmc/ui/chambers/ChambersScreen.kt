package com.rootrecord.rootmc.ui.chambers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.WorldPlayMode
import com.rootrecord.rootmc.domain.model.SpawnerCooldown
import com.rootrecord.rootmc.ui.components.MinecraftCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChambersScreen(
    onOpenChamber: (Long) -> Unit,
    viewModel: ChambersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.chambers_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.nav_chambers),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (uiState.activeWorld?.playMode == WorldPlayMode.MULTIPLAYER.name) {
                        IconButton(onClick = { viewModel.refreshGroupSync() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.chambers_sync_groups))
                        }
                    }
                }
                Text(
                    stringResource(R.string.chambers_trial_spawner_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                uiState.syncMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChamberFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = {
                                Text(
                                    when (filter) {
                                        ChamberFilter.ALL -> stringResource(R.string.chambers_filter_all)
                                        ChamberFilter.READY -> stringResource(R.string.chambers_filter_ready)
                                        ChamberFilter.COOLING -> stringResource(R.string.chambers_filter_cooling)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            if (uiState.filteredCards.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chambers_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(uiState.filteredCards, key = { it.chamber.id }) { card ->
                ChamberListCard(card = card, nowMs = uiState.nowMs, onClick = { onOpenChamber(card.chamber.id) })
            }
        }
    }

    if (uiState.showAddDialog) {
        AddChamberDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissAddDialog() },
            onSave = { viewModel.saveChamber() },
            onLabel = viewModel::updateAddLabel,
            onNotes = viewModel::updateAddNotes,
            onX = viewModel::updateAddX,
            onY = viewModel::updateAddY,
            onZ = viewModel::updateAddZ,
            onDimension = viewModel::updateAddDimension,
            onGroupId = viewModel::updateAddGroupId,
        )
    }
}

@Composable
private fun ChamberListCard(card: ChamberCard, nowMs: Long, onClick: () -> Unit) {
    val statusText = when {
        card.spawners.isEmpty() -> stringResource(R.string.chambers_no_spawners)
        card.readyCount > 0 && card.coolingCount == 0 -> stringResource(R.string.chambers_status_ready)
        card.coolingCount > 0 && card.nextReadyMs != null -> {
            val remaining = (card.nextReadyMs - nowMs).coerceAtLeast(0L)
            stringResource(R.string.chambers_status_cooling, SpawnerCooldown.formatRemaining(remaining))
        }
        card.farmingCount > 0 -> stringResource(R.string.chambers_status_farming)
        else -> stringResource(R.string.chambers_status_mixed)
    }
    val statusColor = when {
        card.readyCount > 0 && card.coolingCount == 0 -> MaterialTheme.colorScheme.primary
        card.coolingCount > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    MinecraftCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Text(card.chamber.label, style = MaterialTheme.typography.titleMedium)
        card.chamber.realmGroupId?.let {
            Text(
                stringResource(R.string.chambers_group_linked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        val coords = listOfNotNull(card.chamber.x, card.chamber.y, card.chamber.z)
        if (coords.size == 3) {
            Text(
                "${card.chamber.dimension} · ${coords[0]} ${coords[1]} ${coords[2]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(
                R.string.chambers_spawner_counts,
                card.readyCount,
                card.coolingCount,
                card.spawners.size,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChamberDialog(
    uiState: ChambersUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onLabel: (String) -> Unit,
    onNotes: (String) -> Unit,
    onX: (String) -> Unit,
    onY: (String) -> Unit,
    onZ: (String) -> Unit,
    onDimension: (MinecraftDimension) -> Unit,
    onGroupId: (String?) -> Unit,
) {
    var dimExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(false) }
    val isMultiplayer = uiState.activeWorld?.playMode == WorldPlayMode.MULTIPLAYER.name
    val selectedGroupName = uiState.realmGroups.find { it.id == uiState.addGroupId }?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chambers_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.addLabel,
                    onValueChange = onLabel,
                    label = { Text(stringResource(R.string.chambers_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.addNotes,
                    onValueChange = onNotes,
                    label = { Text(stringResource(R.string.chambers_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.addX, onValueChange = onX, label = { Text("X") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = uiState.addY, onValueChange = onY, label = { Text("Y") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = uiState.addZ, onValueChange = onZ, label = { Text("Z") }, modifier = Modifier.weight(1f))
                }
                ExposedDropdownMenuBox(expanded = dimExpanded, onExpandedChange = { dimExpanded = it }) {
                    OutlinedTextField(
                        value = uiState.addDimension.name.lowercase().replaceFirstChar { c -> c.titlecase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.chambers_dimension)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dimExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = dimExpanded, onDismissRequest = { dimExpanded = false }) {
                        MinecraftDimension.entries.forEach { dim ->
                            DropdownMenuItem(
                                text = { Text(dim.name.lowercase().replaceFirstChar { c -> c.titlecase() }) },
                                onClick = { onDimension(dim); dimExpanded = false },
                            )
                        }
                    }
                }
                if (isMultiplayer && uiState.realmGroups.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                        OutlinedTextField(
                            value = selectedGroupName ?: stringResource(R.string.chambers_group_none),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.chambers_group_share)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chambers_group_none)) },
                                onClick = { onGroupId(null); groupExpanded = false },
                            )
                            uiState.realmGroups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = { onGroupId(group.id); groupExpanded = false },
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.chambers_group_share_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
