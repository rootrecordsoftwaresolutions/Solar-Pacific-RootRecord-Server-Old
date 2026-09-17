package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.AreaDao
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.NotebookPreset
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.sync.CloudBackupCoordinator
import com.rootrecord.rootmc.util.CoordinateMath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AreaRepository @Inject constructor(
    private val areaDao: AreaDao,
    private val notebookDao: NotebookDao,
    private val cloudBackup: CloudBackupCoordinator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeByWorld(worldId: Long): Flow<List<AreaEntity>> =
        areaDao.observeByWorld(worldId)

    fun observeById(areaId: Long): Flow<AreaEntity?> =
        areaDao.observeById(areaId)

    suspend fun getById(id: Long): AreaEntity? = withContext(io) {
        areaDao.getById(id)
    }

    suspend fun getOrCreateLocationsNotebook(worldId: Long): Long = withContext(io) {
        notebookDao.findByName(worldId, LOCATIONS_NOTEBOOK_NAME)?.id ?: run {
            val sortOrder = notebookDao.countByWorld(worldId)
            notebookDao.insert(
                NotebookEntity(
                    worldId = worldId,
                    name = LOCATIONS_NOTEBOOK_NAME,
                    icon = "place",
                    preset = NotebookPreset.CUSTOM.name,
                    sortOrder = sortOrder,
                ),
            )
        }
    }

    suspend fun ensureChunkAreaForBlock(
        worldId: Long,
        blockX: Int,
        blockZ: Int,
        dimension: MinecraftDimension,
        labelHint: String = "",
    ): Long = withContext(io) {
        val bounds = CoordinateMath.chunkBoundsForBlock(blockX, blockZ)
        val existing = areaDao.findChunkArea(
            worldId = worldId,
            dimension = dimension.name,
            chunkX = bounds.chunkX,
            chunkZ = bounds.chunkZ,
        )
        if (existing != null) return@withContext existing.id
        val label = labelHint.ifBlank {
            "Chunk ${bounds.chunkX}, ${bounds.chunkZ}"
        }
        val nextOrder = areaDao.maxSortOrderForWorld(worldId) + 1
        areaDao.insert(
            AreaEntity(
                worldId = worldId,
                label = label,
                minX = bounds.minX,
                minZ = bounds.minZ,
                maxX = bounds.maxX,
                maxZ = bounds.maxZ,
                dimension = dimension.name,
                chunkArea = true,
                chunkX = bounds.chunkX,
                chunkZ = bounds.chunkZ,
                timestamp = System.currentTimeMillis(),
                sortOrder = nextOrder,
            ),
        )
    }

    suspend fun saveFromTwoCorners(
        worldId: Long,
        x1: Int,
        z1: Int,
        x2: Int,
        z2: Int,
        dimension: MinecraftDimension,
        label: String = "",
    ): Long = withContext(io) {
        val rect = CoordinateMath.rectangleFromCorners(x1, z1, x2, z2)
        val nextOrder = areaDao.maxSortOrderForWorld(worldId) + 1
        areaDao.insert(
            AreaEntity(
                worldId = worldId,
                label = label.trim().ifBlank {
                    CoordinateMath.formatAreaBounds(rect.minX, rect.maxX, rect.minZ, rect.maxZ)
                },
                minX = rect.minX,
                minZ = rect.minZ,
                maxX = rect.maxX,
                maxZ = rect.maxZ,
                dimension = dimension.name,
                chunkArea = false,
                chunkX = null,
                chunkZ = null,
                timestamp = System.currentTimeMillis(),
                sortOrder = nextOrder,
            ),
        ).also { cloudBackup.scheduleBackupAfterLocalChange() }
    }

    suspend fun update(area: AreaEntity) = withContext(io) {
        areaDao.update(area)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun delete(id: Long) = withContext(io) {
        areaDao.deleteById(id)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    companion object {
        const val LOCATIONS_NOTEBOOK_NAME = "Locations"
    }
}
