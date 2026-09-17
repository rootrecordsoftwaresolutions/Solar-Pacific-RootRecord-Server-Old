package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ExportNote(
    val id: Long,
    val title: String,
    val markdownBody: String,
    val notebookId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
)

@Serializable
private data class ExportNotebook(
    val id: Long,
    val worldId: Long,
    val name: String,
    val preset: String,
)

@Serializable
private data class ExportWorld(
    val id: Long,
    val name: String,
    val seed: String?,
    val gameVersion: String,
)

@Serializable
private data class RootMCExport(
    val version: Int = 1,
    val exportedAt: Long,
    val worlds: List<ExportWorld>,
    val notebooks: List<ExportNotebook>,
    val notes: List<ExportNote>,
)

@Singleton
class ExportRepository @Inject constructor(
    private val worldDao: WorldDao,
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun exportJson(): String = withContext(io) {
        val worlds = worldDao.observeAll().first().map {
            ExportWorld(it.id, it.name, it.seed, it.gameVersion)
        }
        val notebooks = mutableListOf<ExportNotebook>()
        val notes = mutableListOf<ExportNote>()
        for (world in worlds) {
            val worldNotebooks = notebookDao.observeByWorld(world.id).first()
            notebooks += worldNotebooks.map {
                ExportNotebook(it.id, it.worldId, it.name, it.preset)
            }
            for (nb in worldNotebooks) {
                val nbNotes = noteDao.observeByNotebook(nb.id).first()
                notes += nbNotes.map {
                    ExportNote(
                        id = it.id,
                        title = it.title,
                        markdownBody = it.markdownBody,
                        notebookId = it.notebookId,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        pinned = it.pinned,
                    )
                }
            }
        }
        AppJson.encodeToString(
            RootMCExport.serializer(),
            RootMCExport(
                exportedAt = System.currentTimeMillis(),
                worlds = worlds,
                notebooks = notebooks,
                notes = notes,
            ),
        )
    }

    suspend fun exportMarkdown(): String = withContext(io) {
        val worlds = worldDao.observeAll().first()
        buildString {
            appendLine("# RootMC Export")
            appendLine()
            for (world in worlds) {
                appendLine("## ${world.name}")
                appendLine()
                val notebooks = notebookDao.observeByWorld(world.id).first()
                for (nb in notebooks) {
                    appendLine("### ${nb.name}")
                    appendLine()
                    val nbNotes = noteDao.observeByNotebook(nb.id).first()
                    for (note in nbNotes) {
                        appendLine("#### ${note.title}")
                        appendLine()
                        appendLine(note.markdownBody)
                        appendLine()
                    }
                }
            }
        }.trim()
    }
}
