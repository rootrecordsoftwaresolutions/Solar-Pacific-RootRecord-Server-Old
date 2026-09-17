package com.rootrecord.rootmc.ui.screens



import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyRow

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.CheckBoxOutlineBlank

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material.icons.filled.FormatBold

import androidx.compose.material.icons.filled.FormatItalic

import androidx.compose.material.icons.filled.Image

import androidx.compose.material.icons.filled.MoreVert

import androidx.compose.material.icons.filled.PushPin

import androidx.compose.material.icons.filled.Title

import androidx.compose.material.icons.filled.Visibility

import androidx.compose.material.icons.filled.VisibilityOff

import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted

import androidx.compose.material.icons.outlined.FormatListNumbered

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.DropdownMenu

import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import coil.compose.AsyncImage

import com.rootrecord.rootmc.ui.components.MarkdownPreview

import com.rootrecord.rootmc.ui.notes.NoteEditorViewModel
import com.rootrecord.rootmc.util.EntryTimestamp

import java.io.File



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun NoteEditorScreen(

    onBack: () -> Unit,

    onOpenNote: (Long) -> Unit = {},

    viewModel: NoteEditorViewModel = hiltViewModel(),

) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val media by viewModel.media.collectAsStateWithLifecycle()

    val linkedNotes by viewModel.linkedNotes.collectAsStateWithLifecycle()

    val linkCandidates by viewModel.linkCandidates.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }

    var confirmDelete by remember { mutableStateOf(false) }



    LaunchedEffect(Unit) {

        viewModel.deleted.collect { onBack() }

    }



    val imagePicker = rememberLauncherForActivityResult(

        contract = ActivityResultContracts.GetContent(),

    ) { uri: Uri? ->

        uri?.let { viewModel.attachImage(it) }

    }



    if (confirmDelete) {

        AlertDialog(

            onDismissRequest = { confirmDelete = false },

            title = { Text("Move to trash?") },

            text = { Text("You can restore this note from Trash in the drawer.") },

            confirmButton = {

                TextButton(onClick = {

                    confirmDelete = false

                    viewModel.moveToTrash()

                }) { Text("Move to trash") }

            },

            dismissButton = {

                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }

            },

        )

    }



    if (uiState.showLinkPicker) {

        AlertDialog(

            onDismissRequest = { viewModel.dismissLinkPicker() },

            title = { Text("Link a note") },

            text = {

                Column {

                    OutlinedTextField(

                        value = uiState.linkSearchQuery,

                        onValueChange = viewModel::onLinkSearchChange,

                        label = { Text("Search notes") },

                        modifier = Modifier.fillMaxWidth(),

                        singleLine = true,

                    )

                    linkCandidates.forEach { candidate ->

                        TextButton(

                            onClick = { viewModel.addLink(candidate.id) },

                            modifier = Modifier.fillMaxWidth(),

                        ) {

                            Text(candidate.title)

                        }

                    }

                    if (linkCandidates.isEmpty()) {

                        Text(

                            "No matching notes in this world",

                            style = MaterialTheme.typography.bodySmall,

                            modifier = Modifier.padding(top = 8.dp),

                        )

                    }

                }

            },

            confirmButton = {

                TextButton(onClick = { viewModel.dismissLinkPicker() }) { Text("Done") }

            },

        )

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {

                        if (uiState.pinned) {

                            Icon(

                                Icons.Default.PushPin,

                                contentDescription = "Pinned",

                                modifier = Modifier.padding(end = 4.dp),

                                tint = MaterialTheme.colorScheme.primary,

                            )

                        }

                        Text(

                            when {

                                uiState.saving -> "Saving…"

                                uiState.saved -> "Saved"

                                else -> "Edit note"

                            },

                        )

                    }

                },

                navigationIcon = {

                    IconButton(onClick = {

                        viewModel.saveNow()

                        onBack()

                    }) {

                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")

                    }

                },

                actions = {

                    IconButton(onClick = { viewModel.togglePreview() }) {

                        Icon(

                            if (uiState.showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,

                            contentDescription = "Toggle preview",

                        )

                    }

                    IconButton(onClick = { viewModel.togglePinned() }) {

                        Icon(

                            Icons.Default.PushPin,

                            contentDescription = "Pin note",

                            tint = if (uiState.pinned) MaterialTheme.colorScheme.primary

                            else MaterialTheme.colorScheme.onSurface,

                        )

                    }

                    IconButton(onClick = { menuOpen = true }) {

                        Icon(Icons.Default.MoreVert, contentDescription = "More")

                    }

                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {

                        DropdownMenuItem(

                            text = { Text("Move to trash") },

                            onClick = {

                                menuOpen = false

                                confirmDelete = true

                            },

                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },

                        )

                    }

                },

            )

        },

    ) { padding ->

        Column(

            modifier = Modifier

                .fillMaxSize()

                .padding(padding)

                .padding(horizontal = 16.dp)

                .verticalScroll(rememberScrollState()),

        ) {

            Row(

                modifier = Modifier

                    .fillMaxWidth()

                    .horizontalScroll(rememberScrollState()),

                horizontalArrangement = Arrangement.spacedBy(4.dp),

            ) {

                IconButton(onClick = { viewModel.applyMarkdownWrap("**") }) {

                    Icon(Icons.Default.FormatBold, contentDescription = "Bold")

                }

                IconButton(onClick = { viewModel.applyMarkdownWrap("*") }) {

                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic")

                }

                IconButton(onClick = { viewModel.insertHeading() }) {

                    Icon(Icons.Default.Title, contentDescription = "Heading")

                }

                IconButton(onClick = { viewModel.insertBullet() }) {

                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Bullet list")

                }

                IconButton(onClick = { viewModel.insertNumbered() }) {

                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Numbered list")

                }

                IconButton(onClick = { viewModel.insertChecklist() }) {

                    Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = "Checklist")

                }

                IconButton(onClick = { imagePicker.launch("image/*") }) {

                    Icon(Icons.Default.Image, contentDescription = "Attach image")

                }

            }



            OutlinedTextField(

                value = uiState.title,

                onValueChange = viewModel::onTitleChange,

                label = { Text("Title") },

                modifier = Modifier.fillMaxWidth(),

                singleLine = true,

            )



            if (uiState.showPreview) {

                Text("Preview", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))

                MarkdownPreview(

                    markdown = uiState.body,

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(vertical = 8.dp),

                )

            } else {

                OutlinedTextField(

                    value = uiState.body,

                    onValueChange = viewModel::onBodyChange,

                    label = { Text("Markdown body") },

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(top = 8.dp)

                        .height(240.dp),

                    minLines = 10,

                )

            }



            OutlinedTextField(

                value = uiState.tagsText,

                onValueChange = viewModel::onTagsChange,

                label = { Text("Tags (comma-separated)") },

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(vertical = 8.dp),

                singleLine = true,

            )

            if (uiState.createdAt > 0L) {
                EntryTimestamp(epochMillis = uiState.createdAt, prefix = "Created")
            }
            if (uiState.updatedAt > 0L && uiState.updatedAt != uiState.createdAt) {
                EntryTimestamp(epochMillis = uiState.updatedAt, prefix = "Edited")
            }

            Text("Related notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

            linkedNotes.forEach { linked ->

                Column(modifier = Modifier.fillMaxWidth()) {

                    Row(

                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,

                    ) {

                        TextButton(onClick = { onOpenNote(linked.id) }) {

                            Text(linked.title)

                        }

                        IconButton(onClick = { viewModel.removeLink(linked.id) }) {

                            Icon(Icons.Default.Delete, contentDescription = "Remove link")

                        }

                    }

                    EntryTimestamp(epochMillis = linked.createdAt, prefix = "Created")

                }

            }

            TextButton(onClick = { viewModel.showLinkPicker() }) {
                Text("Link another note")
            }



            if (media.isNotEmpty()) {

                Text("Attachments", style = MaterialTheme.typography.titleSmall)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    items(media, key = { it.id }) { attachment ->

                        AsyncImage(

                            model = File(attachment.localUri),

                            contentDescription = "Attachment",

                            modifier = Modifier

                                .height(96.dp)

                                .padding(vertical = 8.dp),

                            contentScale = ContentScale.Crop,

                        )

                    }

                }

            }

        }

    }

}


