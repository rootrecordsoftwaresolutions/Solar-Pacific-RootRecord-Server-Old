package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.repository.NoteRepository
import javax.inject.Inject

class UpdateNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository,
) {
    suspend operator fun invoke(
        noteId: Long,
        title: String,
        markdownBody: String,
        pinned: Boolean,
        colorArgb: Int?,
        tagNames: List<String>? = null,
    ) = noteRepository.update(
        noteId = noteId,
        title = title,
        markdownBody = markdownBody,
        pinned = pinned,
        colorArgb = colorArgb,
        tagNames = tagNames,
    )
}
