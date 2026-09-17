package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.TagDao
import com.rootrecord.rootmc.data.local.entity.NoteEntity
import com.rootrecord.rootmc.data.local.entity.NoteTagCrossRef
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.sync.CloudBackupCoordinator
import com.rootrecord.rootmc.util.MarkdownUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val cloudBackup: CloudBackupCoordinator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeByNotebook(notebookId: Long): Flow<List<NoteEntity>> =
        noteDao.observeByNotebook(notebookId)

    fun observeById(noteId: Long): Flow<NoteEntity?> = noteDao.observeById(noteId)

    fun observeByWorld(worldId: Long): Flow<List<NoteEntity>> = noteDao.observeByWorld(worldId)

    fun observeByWaypoint(waypointId: Long): Flow<List<NoteEntity>> =
        noteDao.observeByWaypoint(waypointId)

    fun observeByArea(areaId: Long): Flow<List<NoteEntity>> =
        noteDao.observeByArea(areaId)

    fun observeTrash(): Flow<List<NoteEntity>> = noteDao.observeTrash()

    fun observeByTags(tagIds: List<Long>): Flow<List<NoteEntity>> =
        noteDao.observeByTags(tagIds)

    suspend fun getById(noteId: Long): NoteEntity? = withContext(io) {
        noteDao.getById(noteId)
    }

    suspend fun create(
        notebookId: Long,
        title: String,
        markdownBody: String = "",
        pinned: Boolean = false,
        colorArgb: Int? = null,
        tagNames: List<String> = emptyList(),
        waypointId: Long? = null,
        areaId: Long? = null,
    ): Long = withContext(io) {
        val now = System.currentTimeMillis()
        val preview = MarkdownUtils.stripMarkdown(markdownBody)
        val noteId = noteDao.insert(
            NoteEntity(
                notebookId = notebookId,
                waypointId = waypointId,
                areaId = areaId,
                title = title.trim(),
                markdownBody = markdownBody,
                plainTextPreview = preview,
                createdAt = now,
                updatedAt = now,
                pinned = pinned,
                colorArgb = colorArgb,
            ),
        )
        applyTags(noteId, tagNames)
        cloudBackup.scheduleBackupAfterLocalChange()
        noteId
    }

    suspend fun update(
        noteId: Long,
        title: String,
        markdownBody: String,
        pinned: Boolean,
        colorArgb: Int?,
        tagNames: List<String>? = null,
    ) = withContext(io) {
        val existing = noteDao.getById(noteId) ?: return@withContext
        val now = System.currentTimeMillis()
        noteDao.update(
            existing.copy(
                title = title.trim(),
                markdownBody = markdownBody,
                plainTextPreview = MarkdownUtils.stripMarkdown(markdownBody),
                pinned = pinned,
                colorArgb = colorArgb,
                updatedAt = now,
            ),
        )
        if (tagNames != null) applyTags(noteId, tagNames)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun softDelete(noteId: Long) = withContext(io) {
        noteDao.softDelete(noteId)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun restore(noteId: Long) = withContext(io) {
        noteDao.restore(noteId)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun hardDelete(noteId: Long) = withContext(io) {
        tagDao.clearTagsForNote(noteId)
        noteDao.hardDelete(noteId)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun emptyTrash() = withContext(io) {
        noteDao.trashIds().forEach { tagDao.clearTagsForNote(it) }
        noteDao.deleteAllTrash()
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun search(query: String): List<NoteEntity> = withContext(io) {
        val ftsQuery = formatFtsQuery(query)
        if (ftsQuery.isBlank()) emptyList() else noteDao.searchFts(ftsQuery)
    }

    fun observeSearch(query: String): Flow<List<NoteEntity>> {
        val ftsQuery = formatFtsQuery(query)
        return if (ftsQuery.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            noteDao.observeSearchFts(ftsQuery)
        }
    }

    suspend fun getTagsForNote(noteId: Long) = withContext(io) {
        tagDao.getTagsForNote(noteId)
    }

    fun observeTagsForNote(noteId: Long) = tagDao.observeTagsForNote(noteId)

    private suspend fun applyTags(noteId: Long, tagNames: List<String>) {
        tagDao.clearTagsForNote(noteId)
        tagNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .forEach { name ->
                val tagId = tagDao.findByName(name)?.id ?: tagDao.insert(
                    com.rootrecord.rootmc.data.local.entity.TagEntity(name = name),
                )
                tagDao.insertCrossRef(NoteTagCrossRef(noteId = noteId, tagId = tagId))
            }
    }

    private fun formatFtsQuery(raw: String): String {
        return raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                val escaped = token.replace("\"", "\"\"")
                "\"$escaped\"*"
            }
    }
}
