package com.rootrecord.rootmc.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChamberDao {
    @Query("SELECT * FROM trial_chambers WHERE worldId = :worldId ORDER BY sortOrder ASC, label ASC")
    fun observeChambersByWorld(worldId: Long): Flow<List<TrialChamberEntity>>

    @Query("SELECT * FROM trial_chambers WHERE id = :id")
    suspend fun getChamberById(id: Long): TrialChamberEntity?

    @Query("SELECT * FROM trial_chambers WHERE id = :id")
    fun observeChamberById(id: Long): Flow<TrialChamberEntity?>

    @Query("SELECT * FROM trial_chambers WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findChamberByRemoteId(remoteId: String): TrialChamberEntity?

    @Query("SELECT * FROM trial_chambers WHERE realmGroupId = :groupId ORDER BY sortOrder ASC, label ASC")
    suspend fun listChambersForGroup(groupId: String): List<TrialChamberEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM trial_chambers WHERE worldId = :worldId")
    suspend fun maxChamberSortOrder(worldId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChamber(chamber: TrialChamberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChambers(chambers: List<TrialChamberEntity>): List<Long>

    @Update
    suspend fun updateChamber(chamber: TrialChamberEntity)

    @Delete
    suspend fun deleteChamber(chamber: TrialChamberEntity)

    @Query("DELETE FROM trial_chambers WHERE id = :id")
    suspend fun deleteChamberById(id: Long)

    @Query("DELETE FROM trial_chambers WHERE realmGroupId = :groupId")
    suspend fun deleteChambersForGroup(groupId: String)

    @Query("SELECT * FROM spawner_tracks WHERE chamberId = :chamberId ORDER BY sortOrder ASC, label ASC")
    fun observeSpawnersForChamber(chamberId: Long): Flow<List<SpawnerTrackEntity>>

    @Query("SELECT * FROM spawner_tracks WHERE chamberId = :chamberId ORDER BY sortOrder ASC, label ASC")
    suspend fun listSpawnersForChamber(chamberId: Long): List<SpawnerTrackEntity>

    @Query(
        """
        SELECT spawner_tracks.* FROM spawner_tracks
        INNER JOIN trial_chambers ON spawner_tracks.chamberId = trial_chambers.id
        WHERE trial_chambers.worldId = :worldId
        ORDER BY trial_chambers.sortOrder ASC, spawner_tracks.sortOrder ASC
        """,
    )
    fun observeSpawnersByWorld(worldId: Long): Flow<List<SpawnerTrackEntity>>

    @Query("SELECT * FROM spawner_tracks WHERE id = :id")
    suspend fun getSpawnerById(id: Long): SpawnerTrackEntity?

    @Query("SELECT * FROM spawner_tracks WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findSpawnerByRemoteId(remoteId: String): SpawnerTrackEntity?

    @Query("SELECT * FROM spawner_tracks WHERE chamberId IN (SELECT id FROM trial_chambers WHERE realmGroupId = :groupId)")
    suspend fun listSpawnersForGroup(groupId: String): List<SpawnerTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpawner(spawner: SpawnerTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpawners(spawners: List<SpawnerTrackEntity>): List<Long>

    @Update
    suspend fun updateSpawner(spawner: SpawnerTrackEntity)

    @Delete
    suspend fun deleteSpawner(spawner: SpawnerTrackEntity)

    @Query("DELETE FROM spawner_tracks WHERE chamberId = :chamberId")
    suspend fun deleteSpawnersForChamber(chamberId: Long)

    @Query("DELETE FROM spawner_tracks WHERE chamberId IN (SELECT id FROM trial_chambers WHERE realmGroupId = :groupId)")
    suspend fun deleteSpawnersForGroup(groupId: String)

    @Query("SELECT * FROM trial_chambers ORDER BY worldId ASC, sortOrder ASC, id ASC")
    suspend fun allChambers(): List<TrialChamberEntity>

    @Query("SELECT * FROM spawner_tracks ORDER BY chamberId ASC, sortOrder ASC, id ASC")
    suspend fun allSpawners(): List<SpawnerTrackEntity>

    @Query("DELETE FROM spawner_tracks")
    suspend fun clearAllSpawners()

    @Query("DELETE FROM trial_chambers")
    suspend fun clearAllChambers()

    @Transaction
    suspend fun replaceGroupChambers(
        groupId: String,
        chambers: List<TrialChamberEntity>,
        spawners: List<SpawnerTrackEntity>,
    ) {
        deleteSpawnersForGroup(groupId)
        deleteChambersForGroup(groupId)
        if (chambers.isNotEmpty()) insertChambers(chambers)
        if (spawners.isNotEmpty()) insertSpawners(spawners)
    }
}
