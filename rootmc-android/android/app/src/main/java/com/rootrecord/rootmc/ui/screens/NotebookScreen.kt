package com.rootrecord.rootmc.ui.screens



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

import com.rootrecord.rootmc.ui.components.MinecraftCard

import com.rootrecord.rootmc.ui.notes.NotebookViewModel

import com.rootrecord.rootmc.util.EntryTimestamp



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun NotebookScreen(

    onBack: () -> Unit,

    onOpenNote: (Long) -> Unit,

    onNewNote: (Long) -> Unit,

    viewModel: NotebookViewModel = hiltViewModel(),

) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text(uiState.notebook?.name ?: stringResource(R.string.nav_notes)) },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")

                    }

                },

            )

        },

        floatingActionButton = {

            FloatingActionButton(onClick = { viewModel.createNote(onNewNote) }) {

                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))

            }

        },

    ) { padding ->

        LazyColumn(

            modifier = Modifier

                .fillMaxSize()

                .padding(padding)

                .padding(horizontal = 16.dp),

            contentPadding = PaddingValues(bottom = 88.dp),

            verticalArrangement = Arrangement.spacedBy(8.dp),

        ) {

            items(uiState.notes, key = { it.id }) { note ->

                val dismissState = rememberSwipeToDismissBoxState(

                    confirmValueChange = { value ->

                        if (value == SwipeToDismissBoxValue.EndToStart) {

                            viewModel.deleteNote(note.id)

                            true

                        } else {

                            false

                        }

                    },

                )

                SwipeToDismissBox(

                    state = dismissState,

                    enableDismissFromStartToEnd = false,

                    backgroundContent = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 24.dp),
                            )
                        }
                    },

                ) {

                    MinecraftCard(

                        modifier = Modifier.fillMaxWidth(),

                        onClick = { onOpenNote(note.id) },

                    ) {

                        androidx.compose.foundation.layout.Row(

                            verticalAlignment = Alignment.CenterVertically,

                        ) {

                            if (note.pinned) {

                                Icon(

                                    Icons.Default.PushPin,

                                    contentDescription = "Pinned",

                                    tint = MaterialTheme.colorScheme.primary,

                                    modifier = Modifier.padding(end = 6.dp),

                                )

                            }

                            Column(modifier = Modifier.weight(1f)) {

                                Text(text = note.title, style = MaterialTheme.typography.titleMedium)

                                if (note.plainTextPreview.isNotBlank()) {

                                    Text(

                                        text = note.plainTextPreview.take(120),

                                        style = MaterialTheme.typography.bodySmall,

                                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                                        maxLines = 2,

                                    )

                                }

                                EntryTimestamp(epochMillis = note.createdAt, prefix = "Created")

                            }

                        }

                    }

                }

            }

        }

    }

}


