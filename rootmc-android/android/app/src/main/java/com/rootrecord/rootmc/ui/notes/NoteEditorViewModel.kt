package com.rootrecord.rootmc.ui.notes



import android.content.Context

import android.net.Uri

import androidx.lifecycle.SavedStateHandle

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.rootrecord.rootmc.data.local.dao.MediaDao

import com.rootrecord.rootmc.data.local.dao.NotebookDao

import com.rootrecord.rootmc.data.local.entity.MediaAttachmentEntity

import com.rootrecord.rootmc.data.local.entity.MediaType

import com.rootrecord.rootmc.data.local.entity.NoteEntity

import com.rootrecord.rootmc.data.repository.NoteLinkRepository

import com.rootrecord.rootmc.data.repository.NoteRepository

import com.rootrecord.rootmc.data.repository.TimelineRepository

import com.rootrecord.rootmc.domain.usecase.CreateNoteUseCase

import com.rootrecord.rootmc.domain.usecase.UpdateNoteUseCase

import dagger.hilt.android.lifecycle.HiltViewModel

import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableSharedFlow

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.flatMapLatest

import kotlinx.coroutines.flow.flowOf

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.launch

import java.io.File

import java.util.UUID

import javax.inject.Inject



data class NoteEditorUiState(

    val noteId: Long = 0L,

    val isNew: Boolean = false,

    val title: String = "",

    val body: String = "",

    val tagsText: String = "",

    val pinned: Boolean = false,

    val showPreview: Boolean = false,

    val saving: Boolean = false,

    val saved: Boolean = true,

    val showLinkPicker: Boolean = false,

    val linkSearchQuery: String = "",

    val createdAt: Long = 0L,

    val updatedAt: Long = 0L,

)



@HiltViewModel

class NoteEditorViewModel @Inject constructor(

    savedStateHandle: SavedStateHandle,

    @ApplicationContext private val context: Context,

    private val notebookDao: NotebookDao,

    private val mediaDao: MediaDao,

    private val noteRepository: NoteRepository,

    private val noteLinkRepository: NoteLinkRepository,

    private val timelineRepository: TimelineRepository,

    private val createNoteUseCase: CreateNoteUseCase,

    private val updateNoteUseCase: UpdateNoteUseCase,

) : ViewModel() {



    private val noteIdArg: Long? = savedStateHandle.get<Long>("noteId")

    private val notebookIdArg: Long? = savedStateHandle.get<Long>("notebookId")



    private val _uiState = MutableStateFlow(NoteEditorUiState())

    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()



    private val _deleted = MutableSharedFlow<Unit>()

    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()



    val media: StateFlow<List<MediaAttachmentEntity>> = _uiState

        .flatMapLatest { state ->

            if (state.noteId > 0L) mediaDao.observeByNote(state.noteId) else flowOf(emptyList())

        }

        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())



    val linkedNotes: StateFlow<List<NoteEntity>> = _uiState

        .flatMapLatest { state ->

            if (state.noteId > 0L) noteLinkRepository.observeLinkedNotes(state.noteId)

            else flowOf(emptyList())

        }

        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())



    private val _linkCandidates = MutableStateFlow<List<NoteEntity>>(emptyList())

    val linkCandidates: StateFlow<List<NoteEntity>> = _linkCandidates.asStateFlow()



    private var saveJob: Job? = null

    private var initialized = false

    private var notebookId: Long = 0L

    private var worldId: Long = 0L



    init {

        viewModelScope.launch {

            when {

                noteIdArg != null -> loadExisting(noteIdArg)

                notebookIdArg != null -> createDraft(notebookIdArg)

            }

        }

    }



    private suspend fun loadExisting(noteId: Long) {

        val note = noteRepository.getById(noteId) ?: return

        notebookId = note.notebookId

        worldId = notebookDao.getById(note.notebookId)?.worldId ?: 0L

        val tags = noteRepository.getTagsForNote(noteId).joinToString(", ") { it.name }

        _uiState.value = NoteEditorUiState(

            noteId = noteId,

            isNew = false,

            title = note.title,

            body = note.markdownBody,

            tagsText = tags,

            pinned = note.pinned,

            createdAt = note.createdAt,

            updatedAt = note.updatedAt,

        )

        initialized = true

    }



    private suspend fun createDraft(notebookIdParam: Long) {

        notebookId = notebookIdParam

        worldId = notebookDao.getById(notebookIdParam)?.worldId ?: 0L

        val noteId = createNoteUseCase(notebookId = notebookIdParam, title = "Untitled")

        logTimeline(noteId, "note_created", "Created note")

        _uiState.value = NoteEditorUiState(noteId = noteId, isNew = true, title = "Untitled")

        initialized = true

    }



    fun onTitleChange(value: String) {

        _uiState.update { it.copy(title = value, saved = false) }

        scheduleSave()

    }



    fun onBodyChange(value: String) {

        _uiState.update { it.copy(body = value, saved = false) }

        scheduleSave()

    }



    fun onTagsChange(value: String) {

        _uiState.update { it.copy(tagsText = value, saved = false) }

        scheduleSave()

    }



    fun togglePreview() = _uiState.update { it.copy(showPreview = !it.showPreview) }



    fun togglePinned() {

        _uiState.update { it.copy(pinned = !it.pinned, saved = false) }

        scheduleSave()

    }



    fun applyMarkdownWrap(prefix: String, suffix: String = prefix) {

        _uiState.update { state ->

            val body = state.body

            val wrapped = if (body.isEmpty()) "$prefix$suffix" else "$prefix${body.trim()}$suffix"

            state.copy(body = wrapped, saved = false)

        }

        scheduleSave()

    }



    fun insertHeading() {

        _uiState.update { state ->

            val line = if (state.body.isBlank()) "# " else "\n\n# "

            state.copy(body = state.body + line, saved = false)

        }

        scheduleSave()

    }



    fun insertBullet() {

        _uiState.update { state ->

            val line = if (state.body.isBlank()) "- " else "\n- "

            state.copy(body = state.body + line, saved = false)

        }

        scheduleSave()

    }



    fun insertNumbered() {

        _uiState.update { state ->

            val line = if (state.body.isBlank()) "1. " else "\n1. "

            state.copy(body = state.body + line, saved = false)

        }

        scheduleSave()

    }



    fun insertChecklist() {

        _uiState.update { state ->

            val line = if (state.body.isBlank()) "- [ ] " else "\n- [ ] "

            state.copy(body = state.body + line, saved = false)

        }

        scheduleSave()

    }



    fun attachImage(uri: Uri) {

        val noteId = _uiState.value.noteId

        if (noteId <= 0L) return

        viewModelScope.launch {

            runCatching {

                val dir = File(context.filesDir, "media").apply { mkdirs() }

                val ext = context.contentResolver.getType(uri)?.substringAfter('/') ?: "jpg"

                val file = File(dir, "${UUID.randomUUID()}.$ext")

                context.contentResolver.openInputStream(uri)?.use { input ->

                    file.outputStream().use { output -> input.copyTo(output) }

                } ?: error("cannot_read_uri")

                mediaDao.insert(

                    MediaAttachmentEntity(

                        noteId = noteId,

                        type = MediaType.IMAGE.name,

                        localUri = file.absolutePath,

                        mimeType = context.contentResolver.getType(uri),

                    ),

                )

            }

        }

    }



    fun moveToTrash() {

        val noteId = _uiState.value.noteId

        if (noteId <= 0L) return

        viewModelScope.launch {

            saveJob?.cancel()

            persist()

            noteRepository.softDelete(noteId)

            logTimeline(noteId, "note_deleted", "Moved \"${_uiState.value.title}\" to trash")

            _deleted.emit(Unit)

        }

    }



    private fun scheduleSave() {

        if (!initialized) return

        saveJob?.cancel()

        saveJob = viewModelScope.launch {

            _uiState.update { it.copy(saving = true) }

            delay(600)

            persist()

            _uiState.update { it.copy(saving = false, saved = true) }

        }

    }



    private suspend fun persist() {

        val state = _uiState.value

        if (state.noteId <= 0L) return

        val tagNames = state.tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        updateNoteUseCase(

            noteId = state.noteId,

            title = state.title.ifBlank { "Untitled" },

            markdownBody = state.body,

            pinned = state.pinned,

            colorArgb = null,

            tagNames = tagNames,

        )

        noteRepository.getById(state.noteId)?.let { note ->
            _uiState.update { it.copy(updatedAt = note.updatedAt) }
        }

    }



    private suspend fun logTimeline(noteId: Long, type: String, description: String) {

        val worldId = notebookDao.getById(notebookId)?.worldId ?: return

        timelineRepository.log(worldId, type, description, noteId)

    }



    fun saveNow() {

        viewModelScope.launch {

            _uiState.update { it.copy(saving = true) }

            persist()

            _uiState.update { it.copy(saving = false, saved = true) }

        }

    }



    fun showLinkPicker() {

        viewModelScope.launch {

            refreshLinkCandidates("")

            _uiState.update { it.copy(showLinkPicker = true, linkSearchQuery = "") }

        }

    }



    fun dismissLinkPicker() = _uiState.update { it.copy(showLinkPicker = false, linkSearchQuery = "") }



    fun onLinkSearchChange(query: String) {

        _uiState.update { it.copy(linkSearchQuery = query) }

        viewModelScope.launch { refreshLinkCandidates(query) }

    }



    fun addLink(toNoteId: Long) {

        val fromNoteId = _uiState.value.noteId

        if (fromNoteId <= 0L) return

        viewModelScope.launch {

            noteLinkRepository.link(fromNoteId, toNoteId)

            dismissLinkPicker()

        }

    }



    fun removeLink(toNoteId: Long) {

        val fromNoteId = _uiState.value.noteId

        if (fromNoteId <= 0L) return

        viewModelScope.launch { noteLinkRepository.unlink(fromNoteId, toNoteId) }

    }



    private suspend fun refreshLinkCandidates(query: String) {

        if (worldId <= 0L) return

        _linkCandidates.value = noteLinkRepository.searchCandidates(

            worldId = worldId,

            excludeNoteId = _uiState.value.noteId,

            query = query,

        )

    }

}


