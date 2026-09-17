package com.rootrecord.rootmc.ui.locations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.CoordinateEntity
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.repository.AreaRepository
import com.rootrecord.rootmc.data.repository.CoordinateRepository
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

data class WaypointDetailUiState(
    val waypoint: CoordinateEntity? = null,
    val notes: List<NoteEntity> = emptyList(),
)

@HiltViewModel
class WaypointDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    coordinateRepository: CoordinateRepository,
    noteRepository: NoteRepository,
    private val areaRepository: AreaRepository,
    private val createNoteUseCase: CreateNoteUseCase,
    private val noteRepositoryImpl: NoteRepository,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val waypointId: Long = checkNotNull(savedStateHandle.get<Long>("waypointId"))

    private val waypointFlow = MutableStateFlow<CoordinateEntity?>(null)

    init {
        viewModelScope.launch {
            waypointFlow.value = coordinateRepository.getById(waypointId)
        }
    }

    val uiState: StateFlow<WaypointDetailUiState> = combine(
        waypointFlow,
        noteRepository.observeByWaypoint(waypointId),
    ) { waypoint, notes ->
        WaypointDetailUiState(waypoint = waypoint, notes = notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaypointDetailUiState())

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val waypoint = waypointFlow.value ?: return@launch
            val notebookId = areaRepository.getOrCreateLocationsNotebook(waypoint.worldId)
            val noteId = createNoteUseCase(
                notebookId = notebookId,
                title = "Untitled",
                waypointId = waypointId,
                areaId = waypoint.areaId,
            )
            timelineRepository.log(
                worldId = waypoint.worldId,
                eventType = "note_created",
                description = "Created note at waypoint",
                noteId = noteId,
            )
            onCreated(noteId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepositoryImpl.getById(noteId) ?: return@launch
            val waypoint = waypointFlow.value ?: return@launch
            noteRepositoryImpl.softDelete(noteId)
            timelineRepository.log(
                worldId = waypoint.worldId,
                eventType = "note_deleted",
                description = "Moved \"${note.title}\" to trash",
                noteId = noteId,
            )
        }
    }
}
