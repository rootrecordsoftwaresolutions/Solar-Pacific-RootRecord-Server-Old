package com.rootrecord.rootmc.ui.locations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.repository.AreaRepository
import com.rootrecord.rootmc.data.repository.NoteRepository
import com.rootrecord.rootmc.data.repository.TimelineRepository
import com.rootrecord.rootmc.domain.usecase.CreateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AreaDetailUiState(
    val area: AreaEntity? = null,
    val notes: List<NoteEntity> = emptyList(),
)

@HiltViewModel
class AreaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val areaRepository: AreaRepository,
    noteRepository: NoteRepository,
    private val createNoteUseCase: CreateNoteUseCase,
    private val noteRepositoryImpl: NoteRepository,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val areaId: Long = checkNotNull(savedStateHandle.get<Long>("areaId"))

    private val areaFlow = MutableStateFlow<AreaEntity?>(null)

    init {
        viewModelScope.launch {
            areaFlow.value = areaRepository.getById(areaId)
        }
    }

    val uiState: StateFlow<AreaDetailUiState> = combine(
        areaFlow,
        noteRepository.observeByArea(areaId),
    ) { area, notes ->
        AreaDetailUiState(area = area, notes = notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AreaDetailUiState())

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val area = areaFlow.value ?: return@launch
            val notebookId = areaRepository.getOrCreateLocationsNotebook(area.worldId)
            val noteId = createNoteUseCase(
                notebookId = notebookId,
                title = "Untitled",
                areaId = areaId,
            )
            timelineRepository.log(
                worldId = area.worldId,
                eventType = "note_created",
                description = "Created note in area",
                noteId = noteId,
            )
            onCreated(noteId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepositoryImpl.getById(noteId) ?: return@launch
            val area = areaFlow.value ?: return@launch
            noteRepositoryImpl.softDelete(noteId)
            timelineRepository.log(
                worldId = area.worldId,
                eventType = "note_deleted",
                description = "Moved \"${note.title}\" to trash",
                noteId = noteId,
            )
        }
    }
}
