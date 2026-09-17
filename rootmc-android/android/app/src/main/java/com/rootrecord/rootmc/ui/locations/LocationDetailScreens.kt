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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.CoordinateMath
import com.rootrecord.rootmc.util.EntryTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointDetailScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: WaypointDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val waypoint = uiState.waypoint

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        waypoint?.label?.ifBlank { stringResource(R.string.waypoints_unnamed) }
                            ?: stringResource(R.string.waypoints_unnamed),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createNote(onOpenNote) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
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
            waypoint?.let { wp ->
                item {
                    Text(
                        "${wp.dimension}: ${wp.x}, ${wp.y}, ${wp.z}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    EntryTimestamp(epochMillis = wp.timestamp, prefix = "Saved")
                }
            }
            item {
                Text(
                    stringResource(R.string.location_notes_heading),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (uiState.notes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.location_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.notes, key = { it.id }) { note ->
                LocationNoteRow(
                    note = note,
                    onOpen = { onOpenNote(note.id) },
                    onDelete = { viewModel.deleteNote(note.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaDetailScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: AreaDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val area = uiState.area

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(area?.label ?: stringResource(R.string.nav_areas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createNote(onOpenNote) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
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
            area?.let { a ->
                item {
                    Text(
                        "${a.dimension}: ${CoordinateMath.formatAreaBounds(a.minX, a.maxX, a.minZ, a.maxZ)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (a.chunkArea) {
                        Text(
                            stringResource(R.string.areas_chunk_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    EntryTimestamp(epochMillis = a.timestamp, prefix = "Saved")
                }
            }
            item {
                Text(
                    stringResource(R.string.location_notes_heading),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (uiState.notes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.location_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.notes, key = { it.id }) { note ->
                LocationNoteRow(
                    note = note,
                    onOpen = { onOpenNote(note.id) },
                    onDelete = { viewModel.deleteNote(note.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationNoteRow(
    note: NoteEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        enableDismissFromStartToEnd = false,
    ) {
        MinecraftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                    if (note.plainTextPreview.isNotBlank()) {
                        Text(
                            note.plainTextPreview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                    EntryTimestamp(epochMillis = note.updatedAt, prefix = "Updated")
                }
                if (note.pinned) {
                    Icon(Icons.Default.PushPin, contentDescription = null)
                }
            }
        }
    }
}
