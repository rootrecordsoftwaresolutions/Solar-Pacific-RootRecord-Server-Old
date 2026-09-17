package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.dao.ChamberDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.SpawnerType
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.local.entity.WorldPlayMode
import com.rootrecord.rootmc.data.sync.GroupChambersSnapshot
import com.rootrecord.rootmc.data.sync.toSync
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.domain.model.SpawnerCooldown
import com.rootrecord.rootmc.sync.CloudBackupCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChamberRepository @Inject constructor(
    private val chamberDao: ChamberDao,
    private val worldDao: WorldDao,
    private val realmRepository: RealmRepository,
    private val cloudBackup: CloudBackupCoordinator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeChambersByWorld(worldId: Long): Flow<List<TrialChamberEntity>> =
        chamberDao.observeChambersByWorld(worldId)

    fun observeChamber(chamberId: Long): Flow<TrialChamberEntity?> =
        chamberDao.observeChamberById(chamberId)

    fun observeSpawners(chamberId: Long): Flow<List<SpawnerTrackEntity>> =
        chamberDao.observeSpawnersForChamber(chamberId)

    fun observeSpawnersByWorld(worldId: Long): Flow<List<SpawnerTrackEntity>> =
        chamberDao.observeSpawnersByWorld(worldId)

    suspend fun getChamber(chamberId: Long): TrialChamberEntity? = withContext(io) {
        chamberDao.getChamberById(chamberId)
    }

    suspend fun createChamber(
        world: WorldEntity,
        label: String,
        notes: String = "",
        x: Int? = null,
        y: Int? = null,
        z: Int? = null,
        dimension: String,
        realmGroupId: String? = null,
    ): Long = withContext(io) {
        val sortOrder = chamberDao.maxChamberSortOrder(world.id) + 1
        val now = System.currentTimeMillis()
        val chamber = TrialChamberEntity(
            worldId = world.id,
            worldKey = worldKeyFor(world),
            label = label.trim().ifBlank { "Trial chamber" },
            notes = notes.trim(),
            x = x,
            y = y,
            z = z,
            dimension = dimension,
            realmGroupId = realmGroupId?.takeIf { it.isNotBlank() },
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now,
        )
        val id = chamberDao.insertChamber(chamber)
        realmGroupId?.let { pushGroupSnapshot(it) }
        cloudBackup.scheduleBackupAfterLocalChange()
        id
    }

    suspend fun updateChamber(chamber: TrialChamberEntity) = withContext(io) {
        val updated = chamber.copy(updatedAt = System.currentTimeMillis())
        chamberDao.updateChamber(updated)
        updated.realmGroupId?.let { pushGroupSnapshot(it) }
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun deleteChamber(chamber: TrialChamberEntity) = withContext(io) {
        val groupId = chamber.realmGroupId
        chamberDao.deleteSpawnersForChamber(chamber.id)
        chamberDao.deleteChamberById(chamber.id)
        groupId?.let { pushGroupSnapshot(it) }
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun addSpawner(
        chamberId: Long,
        label: String,
        spawnerType: SpawnerType,
        mobType: String,
        customMobLabel: String?,
        x: Int,
        y: Int,
        z: Int,
        dimension: String,
        notes: String = "",
    ): Long = withContext(io) {
        val chamber = chamberDao.getChamberById(chamberId) ?: return@withContext 0L
        val spawners = chamberDao.listSpawnersForChamber(chamberId)
        val now = System.currentTimeMillis()
        val id = chamberDao.insertSpawner(
            SpawnerTrackEntity(
                chamberId = chamberId,
                label = label.trim().ifBlank { mobType.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() } },
                spawnerType = spawnerType.name,
                mobType = mobType,
                customMobLabel = customMobLabel?.trim()?.takeIf { it.isNotEmpty() },
                x = x,
                y = y,
                z = z,
                dimension = dimension,
                sortOrder = spawners.size,
                updatedAt = now,
            ),
        )
        chamberDao.updateChamber(chamber.copy(updatedAt = now))
        chamber.realmGroupId?.let { pushGroupSnapshot(it) }
        cloudBackup.scheduleBackupAfterLocalChange()
        id
    }

    suspend fun updateSpawner(spawner: SpawnerTrackEntity) = withContext(io) {
        val chamber = chamberDao.getChamberById(spawner.chamberId) ?: return@withContext
        val updated = spawner.copy(updatedAt = System.currentTimeMillis())
        chamberDao.updateSpawner(updated)
        chamberDao.updateChamber(chamber.copy(updatedAt = System.currentTimeMillis()))
        chamber.realmGroupId?.let { pushGroupSnapshot(it) }
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun deleteSpawner(spawner: SpawnerTrackEntity) = withContext(io) {
        val chamber = chamberDao.getChamberById(spawner.chamberId) ?: return@withContext
        chamberDao.deleteSpawner(spawner)
        chamberDao.updateChamber(chamber.copy(updatedAt = System.currentTimeMillis()))
        chamber.realmGroupId?.let { pushGroupSnapshot(it) }
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun markSpawnerCleared(spawnerId: Long, ominousSkip: Boolean = false) = withContext(io) {
        val spawner = chamberDao.getSpawnerById(spawnerId) ?: return@withContext
        val now = System.currentTimeMillis()
        val cooldownEndsAt = when {
            spawner.spawnerType == SpawnerType.MONSTER.name -> null
            ominousSkip -> null
            else -> SpawnerCooldown.defaultCooldownEnd(now)
        }
        updateSpawner(
            spawner.copy(
                lastClearedAt = now,
                cooldownEndsAt = cooldownEndsAt,
            ),
        )
    }

    suspend fun markSpawnerReady(spawnerId: Long) = withContext(io) {
        val spawner = chamberDao.getSpawnerById(spawnerId) ?: return@withContext
        updateSpawner(spawner.copy(cooldownEndsAt = null))
    }

    suspend fun syncGroupChambers(groupId: String, world: WorldEntity): Result<Unit> = withContext(io) {
        runCatching {
            val remote = realmRepository.fetchGroupChambers(groupId).getOrThrow() ?: return@runCatching
            val localChambers = chamberDao.listChambersForGroup(groupId)
            val localRev = localChambers.maxOfOrNull { it.updatedAt } ?: 0L
            if (remote.updatedAt > localRev) {
                importGroupSnapshot(groupId, world, remote.snapshot)
            }
        }
    }

    suspend fun pushGroupSnapshot(groupId: String): Result<Unit> = withContext(io) {
        runCatching {
            val chambers = chamberDao.listChambersForGroup(groupId)
            val chamberRemoteIds = chambers.associate { it.id to it.remoteId }
            val spawners = chamberDao.listSpawnersForGroup(groupId)
            val snapshot = GroupChambersSnapshot(
                updatedAt = System.currentTimeMillis(),
                chambers = chambers.map { it.copy(id = 0L, worldId = 0L) },
                spawnerTracks = spawners.mapNotNull { spawner ->
                    val chamberRemoteId = chamberRemoteIds[spawner.chamberId] ?: return@mapNotNull null
                    spawner.toSync(chamberRemoteId)
                },
            )
            realmRepository.pushGroupChambers(groupId, snapshot).getOrThrow()
            Unit
        }
    }

    private suspend fun importGroupSnapshot(groupId: String, world: WorldEntity, snapshot: GroupChambersSnapshot) {
        val worldKey = worldKeyFor(world)
        val chambers = snapshot.chambers.map { remote ->
            remote.copy(
                id = 0L,
                worldId = world.id,
                worldKey = worldKey,
                realmGroupId = groupId,
            )
        }
        chamberDao.replaceGroupChambers(groupId, emptyList(), emptyList())
        val chamberRemoteToLocal = mutableMapOf<String, Long>()
        for (chamber in chambers) {
            val localId = chamberDao.insertChamber(chamber)
            chamberRemoteToLocal[chamber.remoteId] = localId
        }
        val spawners = snapshot.spawnerTracks.mapNotNull { remote ->
            val chamberId = chamberRemoteToLocal[remote.chamberRemoteId] ?: return@mapNotNull null
            remote.toEntity(chamberId)
        }
        if (spawners.isNotEmpty()) chamberDao.insertSpawners(spawners)
        cloudBackup.scheduleBackupAfterLocalChange()
    }

    suspend fun resolveWorldForGroupChamber(chamber: TrialChamberEntity): WorldEntity? = withContext(io) {
        worldDao.getById(chamber.worldId)
            ?: chamber.worldKey?.let { key -> resolveWorldByKey(key) }
    }

    private suspend fun resolveWorldByKey(worldKey: String): WorldEntity? {
        val parts = worldKey.split('|', limit = 2)
        if (parts.isEmpty()) return null
        val address = parts[0]
        return worldDao.findByServerAddress(address)
    }

    companion object {
        fun worldKeyFor(world: WorldEntity): String? {
            if (world.playMode != WorldPlayMode.MULTIPLAYER.name) return null
            val addr = world.serverAddress?.trim()?.lowercase() ?: return null
            return "$addr|${world.name.trim().lowercase()}"
        }
    }
}
