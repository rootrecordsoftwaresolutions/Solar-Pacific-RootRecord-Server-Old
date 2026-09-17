package com.rootrecord.rootmc.ui.chambers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.rootrecord.rootmc.data.local.entity.SpawnerMobType
import com.rootrecord.rootmc.data.local.entity.SpawnerType
import com.rootrecord.rootmc.domain.model.SpawnerCooldown
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.EntryTimestamp
import com.rootrecord.rootmc.util.TimeFormatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChamberDetailScreen(
    onBack: () -> Unit,
    viewModel: ChamberDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chamber = uiState.chamber

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chamber?.label ?: stringResource(R.string.nav_chambers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddSpawner() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.chambers_add_spawner))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                chamber?.let { ch ->
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        if (ch.notes.isNotBlank()) {
                            Text(ch.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                        ch.realmGroupId?.let {
                            Text(
                                stringResource(R.string.chambers_group_linked),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(
                            stringResource(R.string.chambers_trial_mechanics),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            if (uiState.spawnerRows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chambers_no_spawners_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(uiState.spawnerRows, key = { it.spawner.id }) { row ->
                SpawnerCard(
                    row = row,
                    onMarkCleared = { viewModel.markCleared(row.spawner.id) },
                    onOminousSkip = { viewModel.markCleared(row.spawner.id, ominousSkip = true) },
                    onMarkReady = { viewModel.markReady(row.spawner.id) },
                    onDelete = { viewModel.requestDeleteSpawner(row.spawner) },
                )
            }
        }
    }

    if (uiState.showAddSpawner) {
        AddSpawnerDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissAddSpawner() },
            onSave = { viewModel.saveSpawner() },
            onLabel = viewModel::updateAddLabel,
            onType = viewModel::updateAddType,
            onMob = viewModel::updateAddMob,
            onCustomMob = viewModel::updateAddCustomMob,
            onX = viewModel::updateAddX,
            onY = viewModel::updateAddY,
            onZ = viewModel::updateAddZ,
        )
    }

    uiState.pendingDeleteSpawner?.let { spawner ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteSpawner() },
            title = { Text(stringResource(R.string.chambers_delete_spawner)) },
            text = { Text(spawner.label.ifBlank { spawner.mobType }) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteSpawner() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteSpawner() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SpawnerCard(
    row: SpawnerRow,
    onMarkCleared: () -> Unit,
    onOminousSkip: () -> Unit,
    onMarkReady: () -> Unit,
    onDelete: () -> Unit,
) {
    val spawner = row.spawner
    val mobLabel = spawner.customMobLabel?.takeIf { it.isNotBlank() }
        ?: spawner.mobType.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    val statusLabel = when (row.status) {
        SpawnerCooldown.Status.READY -> stringResource(R.string.chambers_status_ready)
        SpawnerCooldown.Status.COOLING -> stringResource(
            R.string.chambers_status_cooling,
            SpawnerCooldown.formatRemaining(row.remainingMs),
        )
        SpawnerCooldown.Status.FARMING -> stringResource(R.string.chambers_status_farming)
    }
    val statusColor = when (row.status) {
        SpawnerCooldown.Status.READY -> MaterialTheme.colorScheme.primary
        SpawnerCooldown.Status.COOLING -> MaterialTheme.colorScheme.tertiary
        SpawnerCooldown.Status.FARMING -> MaterialTheme.colorScheme.secondary
    }

    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spawner.label.ifBlank { mobLabel }, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${spawner.spawnerType.replace('_', ' ')} · $mobLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${spawner.dimension} · ${spawner.x} ${spawner.y} ${spawner.z}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        Text(statusLabel, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        spawner.lastClearedAt?.let {
            EntryTimestamp(epochMillis = it, prefix = stringResource(R.string.chambers_cleared))
        }
        spawner.cooldownEndsAt?.takeIf { row.status == SpawnerCooldown.Status.COOLING }?.let { endsAt ->
            Text(
                stringResource(R.string.chambers_ready_at, TimeFormatting.formatEpochMillis(endsAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (spawner.spawnerType != SpawnerType.MONSTER.name) {
                OutlinedButton(onClick = onMarkCleared, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chambers_mark_cleared))
                }
            }
            if (row.status == SpawnerCooldown.Status.COOLING) {
                OutlinedButton(onClick = onMarkReady, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chambers_mark_ready))
                }
            }
        }
        if (spawner.spawnerType == SpawnerType.TRIAL.name || spawner.spawnerType == SpawnerType.OMINOUS_TRIAL.name) {
            TextButton(onClick = onOminousSkip) {
                Text(stringResource(R.string.chambers_ominous_skip))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSpawnerDialog(
    uiState: ChamberDetailUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onLabel: (String) -> Unit,
    onType: (SpawnerType) -> Unit,
    onMob: (SpawnerMobType) -> Unit,
    onCustomMob: (String) -> Unit,
    onX: (String) -> Unit,
    onY: (String) -> Unit,
    onZ: (String) -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var mobExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chambers_add_spawner)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.addLabel,
                    onValueChange = onLabel,
                    label = { Text(stringResource(R.string.chambers_spawner_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = uiState.addType.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { c -> c.titlecase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.chambers_spawner_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        SpawnerType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.titlecase() }) },
                                onClick = { onType(type); typeExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = mobExpanded, onExpandedChange = { mobExpanded = it }) {
                    OutlinedTextField(
                        value = if (uiState.addMob == SpawnerMobType.CUSTOM && uiState.addCustomMob.isNotBlank()) {
                            uiState.addCustomMob
                        } else {
                            uiState.addMob.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.titlecase() }
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.chambers_mob_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mobExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = mobExpanded, onDismissRequest = { mobExpanded = false }) {
                        SpawnerMobType.entries.forEach { mob ->
                            DropdownMenuItem(
                                text = { Text(mob.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.titlecase() }) },
                                onClick = { onMob(mob); mobExpanded = false },
                            )
                        }
                    }
                }
                if (uiState.addMob == SpawnerMobType.CUSTOM) {
                    OutlinedTextField(
                        value = uiState.addCustomMob,
                        onValueChange = onCustomMob,
                        label = { Text(stringResource(R.string.chambers_custom_mob)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.addX, onValueChange = onX, label = { Text("X") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = uiState.addY, onValueChange = onY, label = { Text("Y") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = uiState.addZ, onValueChange = onZ, label = { Text("Z") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
