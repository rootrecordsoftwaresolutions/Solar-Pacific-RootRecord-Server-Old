package com.rootrecord.rootmc.ui.locations

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.CoordinateMath
import com.rootrecord.rootmc.util.EntryTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreasScreen(
    onOpenArea: (Long) -> Unit,
    viewModel: AreasViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<AreaEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.areas_add_title))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.areas_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.areas.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.areas_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(uiState.areas, key = { it.id }) { area ->
                AreaCard(
                    area = area,
                    onOpen = { onOpenArea(area.id) },
                    onDelete = { pendingDelete = area },
                )
            }
        }
    }

    if (uiState.showAddDialog) {
        AddAreaDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissAddDialog() },
            onSave = { viewModel.saveArea() },
            onUpdate = viewModel::updateAddField,
        )
    }

    pendingDelete?.let { area ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.areas_delete_title)) },
            text = {
                Text(stringResource(R.string.areas_delete_message, area.label))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteArea(area.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AreaCard(
    area: AreaEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    MinecraftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(area.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${area.dimension}: ${CoordinateMath.formatAreaBounds(area.minX, area.maxX, area.minZ, area.maxZ)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (area.chunkArea) {
                    Text(
                        stringResource(R.string.areas_chunk_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                EntryTimestamp(epochMillis = area.timestamp, prefix = "Saved")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAreaDialog(
    uiState: AreasUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdate: (
        label: String?,
        c1x: String?,
        c1z: String?,
        c2x: String?,
        c2z: String?,
        dimension: MinecraftDimension?,
    ) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.areas_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.areas_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.addLabel,
                    onValueChange = { onUpdate(it, null, null, null, null, null) },
                    label = { Text(stringResource(R.string.areas_label_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.areas_corner_one), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.corner1X,
                        onValueChange = { onUpdate(null, it, null, null, null, null) },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.corner1Z,
                        onValueChange = { onUpdate(null, null, it, null, null, null) },
                        label = { Text("Z") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.areas_corner_two), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.corner2X,
                        onValueChange = { onUpdate(null, null, null, it, null, null) },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.corner2Z,
                        onValueChange = { onUpdate(null, null, null, null, it, null) },
                        label = { Text("Z") },
                        modifier = Modifier.weight(1f),
                    )
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = uiState.addDimension.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Dimension") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MinecraftDimension.entries.forEach { dim ->
                            DropdownMenuItem(
                                text = { Text(dim.name) },
                                onClick = {
                                    onUpdate(null, null, null, null, null, dim)
                                    expanded = false
                                },
                            )
                        }
                    }
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
