package com.rootrecord.rootmc.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "worlds")
data class WorldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val seed: String? = null,
    val gameVersion: String = "1.21",
    /** SINGLEPLAYER or MULTIPLAYER (server). */
    val playMode: String = WorldPlayMode.SINGLEPLAYER.name,
    /** Server address / IP for multiplayer worlds (e.g. play.earthmc.net). */
    val serverAddress: String? = null,
    /** Live map URL (Dynmap, BlueMap, etc.), e.g. https://map.earthmc.net */
    val mapUrl: String? = null,
    val thumbnailUri: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class WorldPlayMode {
    SINGLEPLAYER,
    MULTIPLAYER,
}

@Serializable
enum class NotebookPreset {
    BASES, FARMS, REDSTONE, NETHER, END, TODO, IDEAS, SEEDS, SCREENSHOTS, CUSTOM,
}

@Serializable
@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long,
    val name: String,
    val icon: String = "book",
    val preset: String = NotebookPreset.CUSTOM.name,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long,
    val label: String = "",
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val dimension: String = MinecraftDimension.OVERWORLD.name,
    /** True when auto-created from a waypoint's chunk. */
    val chunkArea: Boolean = false,
    val chunkX: Int? = null,
    val chunkZ: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val waypointId: Long? = null,
    val areaId: Long? = null,
    val title: String,
    val markdownBody: String = "",
    val plainTextPreview: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val colorArgb: Int? = null,
    val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagId"],
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long,
)

@Serializable
@Entity(tableName = "ingame_imports")
data class IngameImportEntity(
    @PrimaryKey val ingameEventId: Long,
    /** `waypoint` or `note` */
    val localType: String,
    val localId: Long,
    val importedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class MinecraftDimension {
    OVERWORLD, NETHER, END,
}

@Serializable
@Entity(tableName = "coordinates")
data class CoordinateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long,
    val noteId: Long? = null,
    val areaId: Long? = null,
    val label: String = "",
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String = MinecraftDimension.OVERWORLD.name,
    val timestamp: Long = System.currentTimeMillis(),
    /** Lower = higher priority (shown first). */
    val sortOrder: Int = 0,
)

@Serializable
enum class MediaType {
    IMAGE, SKETCH, AUDIO,
}

@Serializable
@Entity(tableName = "media_attachments")
data class MediaAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val type: String = MediaType.IMAGE.name,
    val localUri: String,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val ocrText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "note_links",
    primaryKeys = ["fromNoteId", "toNoteId"],
)
data class NoteLinkEntity(
    val fromNoteId: Long,
    val toNoteId: Long,
)

@Serializable
@Entity(tableName = "build_plans")
data class BuildPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long? = null,
    val worldId: Long,
    val title: String,
    val progressPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "build_plan_items")
data class BuildPlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buildPlanId: Long,
    val materialName: String,
    val quantity: Int,
    val obtained: Boolean = false,
)

@Entity(tableName = "reference_cache")
data class ReferenceCacheEntity(
    @PrimaryKey val id: String,
    val category: String,
    val jsonBlob: String,
    val version: String,
)

@Serializable
@Entity(tableName = "timeline_events")
data class ProjectTimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long,
    val noteId: Long? = null,
    val eventType: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Trial chambers (1.21+) and other spawner farms. */
@Serializable
enum class SpawnerType {
    TRIAL, OMINOUS_TRIAL, MONSTER, BREEZE,
}

@Serializable
enum class SpawnerMobType {
    ZOMBIE, SKELETON, SPIDER, CAVE_SPIDER, SILVERFISH, BLAZE, BREEZE, BOGGED,
    SLIME, MAGMA_CUBE, PIGLIN, WITHER_SKELETON, STRAY, HUSK, CUSTOM,
}

@Serializable
@Entity(tableName = "trial_chambers")
data class TrialChamberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id for Realm group sync across devices. */
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val worldId: Long,
    /** server|worldName key for multiplayer group sync when local world ids differ. */
    val worldKey: String? = null,
    val label: String,
    val notes: String = "",
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
    val dimension: String = MinecraftDimension.OVERWORLD.name,
    /** When set, chamber + spawners sync through the Realm group API. */
    val realmGroupId: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "spawner_tracks")
data class SpawnerTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val chamberId: Long,
    val label: String = "",
    val spawnerType: String = SpawnerType.TRIAL.name,
    val mobType: String = SpawnerMobType.ZOMBIE.name,
    val customMobLabel: String? = null,
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String = MinecraftDimension.OVERWORLD.name,
    val lastClearedAt: Long? = null,
    /** Null or past = ready; future = on cooldown. */
    val cooldownEndsAt: Long? = null,
    val notes: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
