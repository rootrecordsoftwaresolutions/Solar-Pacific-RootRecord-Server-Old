package com.rootrecord.rootmc.ui.notes



import androidx.lifecycle.SavedStateHandle

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.rootrecord.rootmc.data.local.dao.NotebookDao

import com.rootrecord.rootmc.data.local.entity.NoteEntity

import com.rootrecord.rootmc.data.local.entity.NotebookEntity

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



data class NotebookUiState(

    val notebook: NotebookEntity? = null,

    val notes: List<NoteEntity> = emptyList(),

)



@HiltViewModel

class NotebookViewModel @Inject constructor(

    savedStateHandle: SavedStateHandle,

    noteRepository: NoteRepository,

    private val notebookDao: NotebookDao,

    private val noteRepositoryImpl: NoteRepository,

    private val timelineRepository: TimelineRepository,

    private val createNoteUseCase: CreateNoteUseCase,

) : ViewModel() {



    private val notebookId: Long = checkNotNull(savedStateHandle.get<Long>("notebookId"))



    private val notebookFlow = MutableStateFlow<NotebookEntity?>(null)



    init {

        viewModelScope.launch {

            notebookFlow.value = notebookDao.getById(notebookId)

        }

    }



    val uiState: StateFlow<NotebookUiState> = combine(

        notebookFlow,

        noteRepository.observeByNotebook(notebookId),

    ) { notebook, notes ->

        NotebookUiState(notebook = notebook, notes = notes)

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())



    fun createNote(onCreated: (Long) -> Unit) {

        viewModelScope.launch {

            val noteId = createNoteUseCase(notebookId = notebookId, title = "Untitled")

            notebookDao.getById(notebookId)?.worldId?.let { worldId ->

                timelineRepository.log(worldId, "note_created", "Created note", noteId)

            }

            onCreated(noteId)

        }

    }



    fun deleteNote(noteId: Long) {

        viewModelScope.launch {

            val note = noteRepositoryImpl.getById(noteId) ?: return@launch

            noteRepositoryImpl.softDelete(noteId)

            notebookDao.getById(notebookId)?.worldId?.let { worldId ->

                timelineRepository.log(worldId, "note_deleted", "Moved \"${note.title}\" to trash", noteId)

            }

        }

    }

}


