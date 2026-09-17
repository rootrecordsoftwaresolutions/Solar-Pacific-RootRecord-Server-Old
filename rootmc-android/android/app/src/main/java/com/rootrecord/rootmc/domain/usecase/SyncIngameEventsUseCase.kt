package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.local.dao.IngameImportDao
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.local.entity.IngameImportEntity
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.NotebookPreset
import com.rootrecord.rootmc.data.repository.CoordinateRepository
import com.rootrecord.rootmc.data.repository.NoteRepository
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncIngameEventsUseCase @Inject constructor(
    private val prefs: RootMcPreferences,
    private val serverRepository: ServerRepository,
    private val syncDedicatedServer: SyncDedicatedServerUseCase,
    private val coordinateRepository: CoordinateRepository,
    private val noteRepository: NoteRepository,
    private val worldDao: WorldDao,
    private val notebookDao: NotebookDao,
    private val ingameImportDao: IngameImportDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun sync(): Result<Int> = withContext(io) {
        if (!prefs.authSignedIn.first()) return@withContext Result.success(0)
        runCatching {
            val config = serverRepository.fetchFeaturedServerConfig().getOrThrow()
            syncDedicatedServer.ensureServerWorld(config, setActive = false)
            val world = worldDao.findByServerAddress(config.address)
                ?: return@runCatching 0

            val events = serverRepository.fetchIngameEvents().getOrThrow()
            if (events.isEmpty()) return@runCatching 0

            val ackIds = mutableListOf<Long>()
            var imported = 0

            for (event in events) {
                if (ingameImportDao.exists(event.id)) continue
                when (event.eventType.lowercase()) {
                    "waypoint" -> {
                        val coordId = coordinateRepository.save(
                            worldId = world.id,
                            x = event.x.toInt(),
                            y = event.y.toInt(),
                            z = event.z.toInt(),
                            dimension = mapDimension(event.dimension),
                            label = event.label?.trim().orEmpty().ifBlank { "Waypoint" },
                        )
                        ingameImportDao.insert(
                            IngameImportEntity(event.id, LOCAL_WAYPOINT, coordId),
                        )
                        ackIds += event.id
                        imported++
                    }
                    "note" -> {
                        val notebookId = ensureIngameNotebook(world.id)
                        val title = event.label?.trim().orEmpty().ifBlank { "In-game note" }
                        val body = event.body?.trim().orEmpty()
                        val noteId = noteRepository.create(
                            notebookId = notebookId,
                            title = title,
                            markdownBody = body,
                        )
                        coordinateRepository.save(
                            worldId = world.id,
                            x = event.x.toInt(),
                            y = event.y.toInt(),
                            z = event.z.toInt(),
                            dimension = mapDimension(event.dimension),
                            label = title,
                            noteId = noteId,
                        )
                        ingameImportDao.insert(
                            IngameImportEntity(event.id, LOCAL_NOTE, noteId),
                        )
                        ackIds += event.id
                        imported++
                    }
                }
            }

            if (ackIds.isNotEmpty()) {
                serverRepository.ackIngameEvents(ackIds)
            }
            imported
        }
    }

    private suspend fun ensureIngameNotebook(worldId: Long): Long {
        val existing = notebookDao.findByName(worldId, INGAME_NOTEBOOK_NAME)
        if (existing != null) return existing.id
        return notebookDao.insert(
            NotebookEntity(
                worldId = worldId,
                name = INGAME_NOTEBOOK_NAME,
                icon = "grass",
                preset = NotebookPreset.IDEAS.name,
                sortOrder = notebookDao.countByWorld(worldId),
            ),
        )
    }

    private fun mapDimension(raw: String): MinecraftDimension = when (raw.lowercase()) {
        "nether", "the_nether" -> MinecraftDimension.NETHER
        "end", "the_end" -> MinecraftDimension.END
        else -> MinecraftDimension.OVERWORLD
    }

    private companion object {
        const val LOCAL_WAYPOINT = "waypoint"
        const val LOCAL_NOTE = "note"
        const val INGAME_NOTEBOOK_NAME = "In-game"
    }
}
