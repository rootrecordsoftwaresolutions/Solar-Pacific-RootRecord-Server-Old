package com.rootrecord.rootmc.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.repository.NoteRepository
import com.rootrecord.rootmc.data.repository.TimelineRepository
import com.rootrecord.rootmc.data.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val showEmptyConfirm: Boolean = false,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val worldRepository: WorldRepository,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = noteRepository.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showEmptyConfirm() = _uiState.update { it.copy(showEmptyConfirm = true) }
    fun dismissEmptyConfirm() = _uiState.update { it.copy(showEmptyConfirm = false) }

    fun restore(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getById(noteId) ?: return@launch
            noteRepository.restore(noteId)
            logForNote(note, "Restored note from trash")
        }
    }

    fun deletePermanently(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepository.getById(noteId) ?: return@launch
            noteRepository.hardDelete(noteId)
            logForNote(note, "Permanently deleted note")
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val trashed = notes.value
            noteRepository.emptyTrash()
            val worldId = worldRepository.getActiveWorld()?.id ?: return@launch
            timelineRepository.log(
                worldId = worldId,
                eventType = "trash_emptied",
                description = "Emptied trash (${trashed.size} notes)",
            )
            dismissEmptyConfirm()
        }
    }

    private suspend fun logForNote(note: NoteEntity, description: String) {
        val worldId = worldRepository.getActiveWorld()?.id ?: return
        timelineRepository.log(
            worldId = worldId,
            eventType = "note_trash",
            description = description,
            noteId = note.id,
        )
    }
}
