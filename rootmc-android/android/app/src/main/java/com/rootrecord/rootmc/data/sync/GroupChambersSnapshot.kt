package com.rootrecord.rootmc.data.sync

import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import kotlinx.serialization.Serializable

/** Spawner row for group sync — uses stable chamber remote id instead of local Room ids. */
@Serializable
data class SyncSpawnerTrack(
    val remoteId: String,
    val chamberRemoteId: String,
    val label: String = "",
    val spawnerType: String,
    val mobType: String,
    val customMobLabel: String? = null,
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String,
    val lastClearedAt: Long? = null,
    val cooldownEndsAt: Long? = null,
    val notes: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toEntity(chamberId: Long): SpawnerTrackEntity = SpawnerTrackEntity(
        remoteId = remoteId,
        chamberId = chamberId,
        label = label,
        spawnerType = spawnerType,
        mobType = mobType,
        customMobLabel = customMobLabel,
        x = x,
        y = y,
        z = z,
        dimension = dimension,
        lastClearedAt = lastClearedAt,
        cooldownEndsAt = cooldownEndsAt,
        notes = notes,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
    )
}

fun SpawnerTrackEntity.toSync(chamberRemoteId: String): SyncSpawnerTrack = SyncSpawnerTrack(
    remoteId = remoteId,
    chamberRemoteId = chamberRemoteId,
    label = label,
    spawnerType = spawnerType,
    mobType = mobType,
    customMobLabel = customMobLabel,
    x = x,
    y = y,
    z = z,
    dimension = dimension,
    lastClearedAt = lastClearedAt,
    cooldownEndsAt = cooldownEndsAt,
    notes = notes,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
)

/** Shared trial-chamber state for a Realm group (multiplayer SMP). */
@Serializable
data class GroupChambersSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val updatedAt: Long = System.currentTimeMillis(),
    val chambers: List<TrialChamberEntity> = emptyList(),
    val spawnerTracks: List<SyncSpawnerTrack> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class GroupChambersRemote(
    val snapshot: GroupChambersSnapshot,
    val updatedAt: Long,
)
