package com.rootrecord.rootmc.data.local

import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.NotebookPreset
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.local.entity.WorldPlayMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val prefs: RootMcPreferences,
    private val worldDao: WorldDao,
    private val notebookDao: NotebookDao,
) {

    suspend fun seedIfNeeded() {
        if (prefs.bootstrapComplete.first()) return
        if (worldDao.count() > 0) {
            prefs.setBootstrapComplete(true)
            return
        }

        val worldId = worldDao.insert(
            WorldEntity(
                name = DEFAULT_WORLD_NAME,
                gameVersion = DEFAULT_GAME_VERSION,
                playMode = WorldPlayMode.MULTIPLAYER.name,
                serverAddress = DEFAULT_SERVER_ADDRESS,
                isActive = true,
                sortOrder = 0,
            ),
        )
        prefs.setDefaultWorldId(worldId)

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

        prefs.setBootstrapComplete(true)
    }

    private data class PresetNotebook(
        val preset: NotebookPreset,
        val displayName: String,
        val icon: String,
    )

    companion object {
        private const val DEFAULT_WORLD_NAME = "RootRecord SMP"
        private const val DEFAULT_SERVER_ADDRESS = ""
        private const val DEFAULT_GAME_VERSION = "26.2"

        private val PRESET_NOTEBOOKS = listOf(
            PresetNotebook(NotebookPreset.BASES, "Bases", "home"),
            PresetNotebook(NotebookPreset.FARMS, "Farms", "grass"),
            PresetNotebook(NotebookPreset.REDSTONE, "Redstone", "redstone"),
            PresetNotebook(NotebookPreset.NETHER, "Nether", "nether"),
            PresetNotebook(NotebookPreset.END, "End", "end"),
            PresetNotebook(NotebookPreset.TODO, "To-Do", "checklist"),
            PresetNotebook(NotebookPreset.IDEAS, "Ideas", "lightbulb"),
            PresetNotebook(NotebookPreset.SEEDS, "Seeds", "seed"),
            PresetNotebook(NotebookPreset.SCREENSHOTS, "Screenshots", "photo"),
        )
    }
}
