package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.local.entity.WorldPlayMode
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.sync.CloudBackupCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorldRepository @Inject constructor(
    private val worldDao: WorldDao,
    private val notebookDao: NotebookDao,
    private val prefs: RootMcPreferences,
    private val cloudBackup: CloudBackupCoordinator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeWorlds(): Flow<List<WorldEntity>> = worldDao.observeAll()

    fun observeActiveWorld(): Flow<WorldEntity?> = worldDao.observeActive()

    fun observeNotebooks(worldId: Long): Flow<List<NotebookEntity>> =
        notebookDao.observeByWorld(worldId)

    suspend fun getWorld(id: Long): WorldEntity? = withContext(io) {
        worldDao.getById(id)
    }

    suspend fun getActiveWorld(): WorldEntity? = withContext(io) {
        worldDao.getActive()
    }

    suspend fun createWorld(
        name: String,
        seed: String? = null,
        gameVersion: String = "1.21",
        playMode: String = WorldPlayMode.SINGLEPLAYER.name,
        serverAddress: String? = null,
        mapUrl: String? = null,
        setActive: Boolean = false,
    ): Long = withContext(io) {
        if (setActive) worldDao.clearActiveFlags()
        val id = worldDao.insert(
            WorldEntity(
                name = name.trim(),
                seed = seed?.trim()?.takeIf { it.isNotEmpty() },
                gameVersion = gameVersion,
                playMode = playMode,
                serverAddress = serverAddress?.trim()?.takeIf { it.isNotEmpty() },
                mapUrl = mapUrl?.trim()?.takeIf { it.isNotEmpty() },
                isActive = setActive,
                sortOrder = worldDao.count(),
            ),
        )
        if (setActive) prefs.setDefaultWorldId(id)
        cloudBackup.scheduleBackupAfterLocalChange()
        id
    }

    suspend fun updateWorld(world: WorldEntity) = withContext(io) {
        worldDao.update(world)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun deleteWorld(world: WorldEntity) = withContext(io) {
        worldDao.delete(world)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun setActiveWorld(worldId: Long) = withContext(io) {
        worldDao.clearActiveFlags()
        worldDao.setActive(worldId)
        prefs.setDefaultWorldId(worldId)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun createNotebook(
        worldId: Long,
        name: String,
        icon: String = "book",
        preset: String = "CUSTOM",
    ): Long = withContext(io) {
        notebookDao.insert(
            NotebookEntity(
                worldId = worldId,
                name = name.trim(),
                icon = icon,
                preset = preset,
                sortOrder = notebookDao.countByWorld(worldId),
            ),
        ).also { cloudBackup.scheduleBackupAfterLocalChange() }
    }

    suspend fun updateNotebook(notebook: NotebookEntity) = withContext(io) {
        notebookDao.update(notebook)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) = withContext(io) {
        notebookDao.delete(notebook)
        cloudBackup.scheduleBackupAfterLocalChange()
    }
}
