package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.local.dao.NoteDao
import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.data.repository.RealmRepository
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRealmProfileUseCase @Inject constructor(
    private val prefs: RootMcPreferences,
    private val worldDao: WorldDao,
    private val noteDao: NoteDao,
    private val realmRepository: RealmRepository,
    private val serverRepository: ServerRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun sync(
        realmUsername: String?,
        bio: String?,
        publicProfile: Boolean = true,
    ): Result<Unit> = withContext(io) {
        runCatching {
            val membership = if (prefs.authSignedIn.first()) {
                serverRepository.fetchMembership().getOrNull()
            } else {
                null
            }
            val sharedIds = prefs.sharedWorldIds.first()
            val worldsArray = buildJsonArray {
                for (worldId in sharedIds) {
                    val world = worldDao.getById(worldId) ?: continue
                    val noteCount = noteDao.observeByWorld(worldId).first().count { !it.deleted }
                    add(
                        buildJsonObject {
                            put("world_key", "local:$worldId")
                            put("world_name", world.name)
                            put("game_version", world.gameVersion)
                            world.seed?.let { put("seed", it) }
                            put("play_mode", world.playMode)
                            world.serverAddress?.takeIf { it.isNotBlank() }?.let { put("server_address", it) }
                            world.mapUrl?.takeIf { it.isNotBlank() }?.let { put("map_url", it) }
                            put("note_count", noteCount)
                            put("is_public", true)
                        },
                    )
                }
            }
            val body = buildJsonObject {
                realmUsername?.trim()?.takeIf { it.isNotEmpty() }?.let { put("realm_username", it) }
                bio?.let { put("bio", it.trim().take(280)) }
                put("public_profile", publicProfile)
                membership?.minecraftUsername?.takeIf { it.isNotBlank() }?.let { put("minecraft_username", it) }
                membership?.minecraftUuid?.takeIf { it.isNotBlank() }?.let { put("minecraft_uuid", it) }
                put("shared_worlds", worldsArray)
            }
            realmRepository.syncProfile(body.toString())
            Unit
        }
    }
}
