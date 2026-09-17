package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchNotesUseCase @Inject constructor(
    private val noteRepository: NoteRepository,
) {
    suspend operator fun invoke(query: String): List<NoteEntity> =
        noteRepository.search(query)

    fun observe(query: String): Flow<List<NoteEntity>> =
        noteRepository.observeSearch(query)
}
