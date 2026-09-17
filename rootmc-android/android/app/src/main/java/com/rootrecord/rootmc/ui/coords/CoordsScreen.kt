package com.rootrecord.rootmc.ui.coords

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.local.entity.CoordinateEntity
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.EntryTimestamp
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val COORD_LIST_HEADER_ITEMS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordsScreen(
    openAddDialogOnLaunch: Boolean = false,
    onOpenWaypoint: (Long) -> Unit = {},
    viewModel: CoordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<CoordinateEntity?>(null) }

    LaunchedEffect(openAddDialogOnLaunch) {
        if (openAddDialogOnLaunch) viewModel.showAddDialog()
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index - COORD_LIST_HEADER_ITEMS
        val toIdx = to.index - COORD_LIST_HEADER_ITEMS
        if (fromIdx in uiState.coordinates.indices && toIdx in uiState.coordinates.indices) {
            viewModel.moveCoordinateInList(fromIdx, toIdx)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Add coordinate")
            }
        },
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Portal calculator", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.portalOverworldX,
                        onValueChange = { viewModel.updatePortalInputs(it, uiState.portalOverworldZ) },
                        label = { Text("OW X") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.portalOverworldZ,
                        onValueChange = { viewModel.updatePortalInputs(uiState.portalOverworldX, it) },
                        label = { Text("OW Z") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(onClick = { viewModel.calculatePortal() }) {
                    Text("Calculate nether link")
                }
                uiState.portalText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Text(
                    "Distance (select two waypoints)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                uiState.pairSummary?.let { summary ->
                    Text(summary.distanceText, style = MaterialTheme.typography.bodyLarge)
                    summary.bearingText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    summary.alignmentText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (uiState.coordinates.isNotEmpty()) {
                    Text(
                        stringResource(R.string.coords_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(uiState.coordinates, key = { it.id }) { coord ->
                ReorderableItem(reorderableState, key = coord.id) { isDragging ->
                    CoordCard(
                        coord = coord,
                        isIngame = uiState.ingameWaypointIds.contains(coord.id),
                        isDragging = isDragging,
                        selected = coord.id == uiState.selectedA || coord.id == uiState.selectedB,
                        selectionLabel = when (coord.id) {
                            uiState.selectedA -> stringResource(R.string.coords_selected_from)
                            uiState.selectedB -> stringResource(R.string.coords_selected_to)
                            else -> null
                        },
                        onSelect = { viewModel.toggleSelect(coord.id) },
                        onEdit = { viewModel.showEditDialog(coord) },
                        onOpenNotes = { onOpenWaypoint(coord.id) },
                        onDelete = { pendingDelete = coord },
                        onCopyTp = { copyToClipboard(context, viewModel.copyTp(coord)) },
                        onCopyPlain = { copyToClipboard(context, viewModel.copyPlain(coord)) },
                    )
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        QuickAddCoordDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissAddDialog() },
            onSave = { viewModel.saveQuickAdd() },
            onUpdate = viewModel::updateAddField,
        )
    }

    pendingDelete?.let { coord ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.coords_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.coords_delete_message,
                        coord.label.ifBlank { stringResource(R.string.coords_unnamed) },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCoordinate(coord.id)
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
private fun ReorderableCollectionItemScope.CoordCard(
    coord: CoordinateEntity,
    isIngame: Boolean = false,
    isDragging: Boolean,
    selected: Boolean,
    selectionLabel: String? = null,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onOpenNotes: () -> Unit,
    onDelete: () -> Unit,
    onCopyTp: () -> Unit,
    onCopyPlain: () -> Unit,
) {
    MinecraftCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.coords_drag_handle),
                modifier = Modifier
                    .longPressDraggableHandle()
                    .padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = coord.label.ifBlank { stringResource(R.string.coords_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${coord.dimension}: ${coord.x}, ${coord.y}, ${coord.z}")
                if (isIngame) {
                    Text(
                        stringResource(R.string.ingame_source_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                EntryTimestamp(epochMillis = coord.timestamp, prefix = "Saved")
                if (selectionLabel != null) {
                    Text(selectionLabel, color = MaterialTheme.colorScheme.primary)
                } else if (selected) {
                    Text(stringResource(R.string.coords_selected), color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (isDragging) {
            Text(
                stringResource(R.string.coords_dragging),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenNotes) { Text(stringResource(R.string.location_notes_heading)) }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            TextButton(onClick = onCopyTp) { Text("/tp") }
            TextButton(onClick = onCopyPlain) { Text("Plain") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddCoordDialog(
    uiState: CoordsUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdate: (label: String?, x: String?, y: String?, z: String?, dimension: MinecraftDimension?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (uiState.editingCoordId != null) {
                    stringResource(R.string.coords_edit_title)
                } else {
                    stringResource(R.string.coords_add_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.addLabel,
                    onValueChange = { onUpdate(it, null, null, null, null) },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.addX,
                        onValueChange = { onUpdate(null, it, null, null, null) },
                        label = { Text("X") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.addY,
                        onValueChange = { onUpdate(null, null, it, null, null) },
                        label = { Text("Y") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.addZ,
                        onValueChange = { onUpdate(null, null, null, it, null) },
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
                                    onUpdate(null, null, null, null, dim)
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

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("coord", text))
}
