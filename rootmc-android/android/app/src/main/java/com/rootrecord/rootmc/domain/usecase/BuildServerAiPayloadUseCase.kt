package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.dao.WorldDao
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildServerAiPayloadUseCase @Inject constructor(
    private val worldDao: WorldDao,
    private val buildWorldAiPayload: BuildWorldAiPayloadUseCase,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun buildForServerAddress(serverAddress: String): Result<List<WorldAiPayload>> = withContext(io) {
        runCatching {
            val worlds = worldDao.listByServerAddress(serverAddress.trim())
            worlds.mapNotNull { world ->
                buildWorldAiPayload.build(world.id).getOrNull()
            }
        }
    }
}
