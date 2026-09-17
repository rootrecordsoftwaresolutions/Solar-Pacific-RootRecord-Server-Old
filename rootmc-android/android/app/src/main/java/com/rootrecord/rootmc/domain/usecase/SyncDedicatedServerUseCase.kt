package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.NotebookPreset
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.local.entity.WorldPlayMode
import com.rootrecord.rootmc.data.repository.FeaturedServerConfig
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.data.repository.WorldRepository
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DedicatedServerSyncResult(
    val worldId: Long?,
    val created: Boolean,
    val belongsToServer: Boolean,
    val config: FeaturedServerConfig,
)

@Singleton
class SyncDedicatedServerUseCase @Inject constructor(
    private val prefs: RootMcPreferences,
    private val serverRepository: ServerRepository,
    private val worldRepository: WorldRepository,
    private val worldDao: com.rootrecord.rootmc.data.local.dao.WorldDao,
    private val notebookDao: NotebookDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun sync(): Result<DedicatedServerSyncResult> = withContext(io) {
        runCatching {
            val config = serverRepository.fetchMobileFeaturedServer().getOrThrow()
            val signedIn = prefs.authSignedIn.first()

            val membership = serverRepository.fetchMembership().getOrNull()
            val shouldEnsureWorld = if (signedIn) {
                membership?.servers?.any { it.autoAddWorld } == true
                    || membership?.minecraftLinked == true
            } else {
                false
            }

            val worldResult = if (shouldEnsureWorld) {
                ensureServerWorld(config, setActive = false)
            } else {
                null to false
            }

            DedicatedServerSyncResult(
                worldId = worldResult?.first,
                created = worldResult?.second == true,
                belongsToServer = signedIn && serverRepository.fetchMembership().getOrNull()
                    ?.servers?.any { it.belongsToServer } == true,
                config = config,
            )
        }
    }

    /** Ensures the featured dedicated server appears in the worlds list. */
    suspend fun ensureServerWorld(
        config: FeaturedServerConfig,
        setActive: Boolean = false,
    ): Pair<Long, Boolean> = withContext(io) {
        val existing = worldDao.findByServerAddress(config.address)
        if (existing != null) {
            val updated = existing.copy(
                name = config.defaultWorldName,
                gameVersion = config.gameVersion,
                playMode = WorldPlayMode.MULTIPLAYER.name,
                serverAddress = config.address,
                mapUrl = config.mapUrl ?: existing.mapUrl,
            )
            worldRepository.updateWorld(updated)
            if (setActive) {
                worldRepository.setActiveWorld(existing.id)
            }
            return@withContext existing.id to false
        }

        val id = worldRepository.createWorld(
            name = config.defaultWorldName,
            gameVersion = config.gameVersion,
            playMode = WorldPlayMode.MULTIPLAYER.name,
            serverAddress = config.address,
            mapUrl = config.mapUrl,
            setActive = setActive,
        )
        seedDefaultNotebooks(id)
        id to true
    }

    private suspend fun seedDefaultNotebooks(worldId: Long) {
        if (notebookDao.countByWorld(worldId) > 0) return
        PRESET_NOTEBOOKS.forEachIndexed { index, preset ->
            notebookDao.insert(
                NotebookEntity(
                    worldId = worldId,
                    name = preset.displayName,
                    icon = preset.icon,
                    preset = preset.preset.name,
                    sortOrder = index,
                ),
            )
        }
    }

    private data class PresetNotebook(
        val preset: NotebookPreset,
        val displayName: String,
        val icon: String,
    )

    private companion object {
        val PRESET_NOTEBOOKS = listOf(
            PresetNotebook(NotebookPreset.BASES, "Bases", "home"),
            PresetNotebook(NotebookPreset.FARMS, "Farms", "grass"),
            PresetNotebook(NotebookPreset.REDSTONE, "Redstone", "redstone"),
            PresetNotebook(NotebookPreset.NETHER, "Nether", "nether"),
            PresetNotebook(NotebookPreset.END, "End", "end"),
            PresetNotebook(NotebookPreset.TODO, "To-Do", "checklist"),
        )
    }
}
