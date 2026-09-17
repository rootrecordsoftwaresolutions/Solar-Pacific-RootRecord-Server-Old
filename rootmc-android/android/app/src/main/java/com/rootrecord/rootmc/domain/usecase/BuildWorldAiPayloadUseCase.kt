package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.local.dao.BuildPlanDao
import com.rootrecord.rootmc.data.local.dao.CoordinateDao
import com.rootrecord.rootmc.data.local.dao.MediaDao
import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.NoteLinkDao
import com.rootrecord.rootmc.data.local.dao.NotebookDao
import com.rootrecord.rootmc.data.local.dao.TagDao
import com.rootrecord.rootmc.data.local.dao.TimelineDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WorldAiPayload(
    val world: WorldAiWorld,
    val minecraft_player: WorldAiPlayer? = null,
    val stats: WorldAiStats,
    val notebooks: List<WorldAiNotebook>,
    val notes: List<WorldAiNote>,
    val coordinates: List<WorldAiCoordinate>,
    val build_plans: List<WorldAiBuildPlan>,
    val timeline: List<WorldAiTimelineEvent>,
    val media: List<WorldAiMedia>,
)

@Serializable
data class WorldAiWorld(
    val local_id: Long,
    val name: String,
    val seed: String? = null,
    val game_version: String,
    val is_active: Boolean,
    val play_mode: String? = null,
    val server_address: String? = null,
    val map_url: String? = null,
)

@Serializable
data class WorldAiPlayer(
    val username: String,
    val uuid: String,
)

@Serializable
data class WorldAiStats(
    val notebook_count: Int,
    val note_count: Int,
    val coord_count: Int,
    val build_plan_count: Int,
    val timeline_event_count: Int,
    val media_count: Int,
)

@Serializable
data class WorldAiNotebook(
    val name: String,
    val preset: String? = null,
    val icon: String? = null,
)

@Serializable
data class WorldAiNote(
    val title: String,
    val notebook_name: String,
    val preview: String,
    val body_excerpt: String,
    val pinned: Boolean,
    val tags: List<String>,
    val linked_note_titles: List<String>,
    val updated_at: Long,
)

@Serializable
data class WorldAiCoordinate(
    val label: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String,
    val note_title: String? = null,
)

@Serializable
data class WorldAiBuildPlan(
    val title: String,
    val progress_percent: Int,
    val items: List<WorldAiBuildItem>,
)

@Serializable
data class WorldAiBuildItem(
    val material_name: String,
    val quantity: Int,
    val obtained: Boolean,
)

@Serializable
data class WorldAiTimelineEvent(
    val event_type: String,
    val description: String,
    val timestamp: Long,
)

@Serializable
data class WorldAiMedia(
    val type: String,
    val note_title: String? = null,
    val mime_type: String? = null,
    val ocr_text: String? = null,
)

@Singleton
class BuildWorldAiPayloadUseCase @Inject constructor(
    private val worldDao: WorldDao,
    private val notebookDao: NotebookDao,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val coordinateDao: CoordinateDao,
    private val buildPlanDao: BuildPlanDao,
    private val timelineDao: TimelineDao,
    private val mediaDao: MediaDao,
    private val noteLinkDao: NoteLinkDao,
    private val prefs: RootMcPreferences,
    private val serverRepository: ServerRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun build(worldId: Long): Result<WorldAiPayload> = withContext(io) {
        runCatching {
            val world = worldDao.getById(worldId) ?: error("World not found.")
            val notebooks = notebookDao.observeByWorld(worldId).first()
            val notes = noteDao.observeByWorld(worldId).first().filter { !it.deleted }
            val notebookById = notebooks.associateBy { it.id }
            val noteTitleById = notes.associate { it.id to it.title }

            val noteRows = notes.take(60).map { note ->
                val tags = tagDao.getTagsForNote(note.id).map { it.name }
                val linked = noteLinkDao.observeLinkedNotes(note.id).first().map { it.title }.take(8)
                WorldAiNote(
                    title = note.title,
                    notebook_name = notebookById[note.notebookId]?.name.orEmpty(),
                    preview = note.plainTextPreview.take(900),
                    body_excerpt = note.markdownBody.take(1800),
                    pinned = note.pinned,
                    tags = tags,
                    linked_note_titles = linked,
                    updated_at = note.updatedAt,
                )
            }

            val coords = coordinateDao.observeByWorld(worldId).first().take(40).map { c ->
                WorldAiCoordinate(
                    label = c.label,
                    x = c.x,
                    y = c.y,
                    z = c.z,
                    dimension = c.dimension,
                    note_title = c.noteId?.let { noteTitleById[it] },
                )
            }

            val plans = buildPlanDao.observeByWorld(worldId).first().take(12).map { plan ->
                val items = buildPlanDao.getItems(plan.id).take(40).map { item ->
                    WorldAiBuildItem(
                        material_name = item.materialName,
                        quantity = item.quantity,
                        obtained = item.obtained,
                    )
                }
                WorldAiBuildPlan(
                    title = plan.title,
                    progress_percent = plan.progressPercent,
                    items = items,
                )
            }

            val timeline = timelineDao.observeByWorld(worldId).first().take(40).map { ev ->
                WorldAiTimelineEvent(
                    event_type = ev.eventType,
                    description = ev.description,
                    timestamp = ev.timestamp,
                )
            }

            val media = mediaDao.observeByWorld(worldId).first().take(24).map { m ->
                WorldAiMedia(
                    type = m.type,
                    note_title = noteTitleById[m.noteId],
                    mime_type = m.mimeType,
                    ocr_text = m.ocrText?.take(400),
                )
            }

            val player = if (prefs.authSignedIn.first()) {
                serverRepository.fetchMembership().getOrNull()?.let { membership ->
                    val user = membership.minecraftUsername
                    val uuid = membership.minecraftUuid
                    if (!user.isNullOrBlank() && !uuid.isNullOrBlank()) {
                        WorldAiPlayer(username = user, uuid = uuid)
                    } else {
                        null
                    }
                }
            } else {
                null
            }

            WorldAiPayload(
                world = WorldAiWorld(
                    local_id = world.id,
                    name = world.name,
                    seed = world.seed,
                    game_version = world.gameVersion,
                    is_active = world.isActive,
                    play_mode = world.playMode,
                    server_address = world.serverAddress,
                    map_url = world.mapUrl,
                ),
                minecraft_player = player,
                stats = WorldAiStats(
                    notebook_count = notebooks.size,
                    note_count = notes.size,
                    coord_count = coords.size,
                    build_plan_count = plans.size,
                    timeline_event_count = timeline.size,
                    media_count = media.size,
                ),
                notebooks = notebooks.map {
                    WorldAiNotebook(name = it.name, preset = it.preset, icon = it.icon)
                },
                notes = noteRows,
                coordinates = coords,
                build_plans = plans,
                timeline = timeline,
                media = media,
            )
        }
    }

    fun worldKey(worldId: Long): String = "local:$worldId"
}
