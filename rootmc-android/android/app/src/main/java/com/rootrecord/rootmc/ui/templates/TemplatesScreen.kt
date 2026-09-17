package com.rootrecord.rootmc.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.data.local.entity.NotebookPreset
import com.rootrecord.rootmc.data.repository.WorldRepository
import com.rootrecord.rootmc.domain.usecase.CreateNoteUseCase
import com.rootrecord.rootmc.ui.components.MinecraftCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteTemplate(
    val title: String,
    val body: String,
    val preset: NotebookPreset,
)

private val TEMPLATES = listOf(
    NoteTemplate(
        "Wheat farm checklist",
        "# Wheat farm\n\n- [ ] Water channels\n- [ ] Composter loop\n- [ ] Villager breeder nearby",
        NotebookPreset.FARMS,
    ),
    NoteTemplate(
        "Iron farm notes",
        "# Iron farm\n\n- Spawn chunks loaded\n- AFK spot: \n- Expected rate: ",
        NotebookPreset.FARMS,
    ),
    NoteTemplate(
        "3-high sugarcane",
        "# Sugarcane\n\n```\n piston line + observer\n```",
        NotebookPreset.FARMS,
    ),
    NoteTemplate(
        "Hidden piston door",
        "# Piston door\n\n1. Double extender\n2. Button on \n3. Fill gap with slime",
        NotebookPreset.REDSTONE,
    ),
    NoteTemplate(
        "Item sorter design",
        "# Sorter\n\n- 41 items per slot filter\n- Overflow to lava\n",
        NotebookPreset.REDSTONE,
    ),
    NoteTemplate(
        "T flip-flop",
        "# T flip-flop\n\n* Input: \n* Output: ",
        NotebookPreset.REDSTONE,
    ),
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val worldRepository: WorldRepository,
    private val createNoteUseCase: CreateNoteUseCase,
) : ViewModel() {

    var lastCreatedNoteId by mutableStateOf<Long?>(null)
        private set

    fun duplicate(template: NoteTemplate, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val world = worldRepository.getActiveWorld() ?: return@launch
            val notebooks = worldRepository.observeNotebooks(world.id).first()
            val notebook = notebooks.find { it.preset == template.preset.name }
                ?: notebooks.firstOrNull()
                ?: return@launch
            val noteId = createNoteUseCase(
                notebookId = notebook.id,
                title = template.title,
                markdownBody = template.body,
            )
            lastCreatedNoteId = noteId
            onCreated(noteId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_templates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Duplicate a template into your active world",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TEMPLATES, key = { it.title }) { template ->
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            template.preset.name,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TextButton(onClick = {
                            viewModel.duplicate(template) { noteId ->
                                message = "Created note"
                                onOpenNote(noteId)
                            }
                        }) {
                            Text("Duplicate")
                        }
                    }
                }
            }
        }
    }
}
