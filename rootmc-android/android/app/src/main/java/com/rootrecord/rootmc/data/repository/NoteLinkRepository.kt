package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.NoteLinkDao
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.local.entity.NoteLinkEntity
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.sync.CloudBackupCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteLinkRepository @Inject constructor(
    private val noteLinkDao: NoteLinkDao,
    private val noteDao: NoteDao,
    private val cloudBackup: CloudBackupCoordinator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeLinkedNotes(fromNoteId: Long): Flow<List<NoteEntity>> =
        noteLinkDao.observeLinkedNotes(fromNoteId)

    suspend fun link(fromNoteId: Long, toNoteId: Long) = withContext(io) {
        if (fromNoteId == toNoteId) return@withContext
        noteLinkDao.insert(NoteLinkEntity(fromNoteId = fromNoteId, toNoteId = toNoteId))
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun unlink(fromNoteId: Long, toNoteId: Long) = withContext(io) {
        noteLinkDao.delete(fromNoteId, toNoteId)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun searchCandidates(
        worldId: Long,
        excludeNoteId: Long,
        query: String,
    ): List<NoteEntity> = withContext(io) {
        noteDao.observeByWorld(worldId).first()
            .asSequence()
            .filter { it.id != excludeNoteId }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .take(25)
            .toList()
    }
}
