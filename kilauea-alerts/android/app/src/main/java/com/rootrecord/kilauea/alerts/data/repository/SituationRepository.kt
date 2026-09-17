package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.local.KilaueaDataDao
import com.rootrecord.kilauea.alerts.data.local.KilaueaDataEntity
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import com.rootrecord.kilauea.alerts.data.remote.RootRecordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class KilaueaSituation(
    val id: String = "current",
    val name: String = "",
    val enabled: Boolean = false,
    val body: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun isActive(): Boolean = enabled && name.isNotBlank()
}

@Serializable
private data class KilaueaSituationResponse(
    val situation: KilaueaSituation? = null,
)

/** D1-backed remote event page — toggled via POST /api/internal/kilauea-situation. */
@Singleton
class SituationRepository @Inject constructor(
    private val api: RootRecordApi,
    private val dao: KilaueaDataDao,
) {
    private val localMaxAgeMs: Long = 2 * 60 * 1000L

    suspend fun cached(): KilaueaSituation? {
        val raw = dao.getByKey(CacheKeys.SITUATION)?.payloadJson ?: return null
        return runCatching { AppJson.decodeFromString(KilaueaSituation.serializer(), raw) }.getOrNull()
    }

    suspend fun refresh(): Result<KilaueaSituation?> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = api.kilaueaSituation()
            val situation = AppJson.decodeFromString(KilaueaSituationResponse.serializer(), raw).situation
            val toStore = situation ?: KilaueaSituation()
            dao.upsert(
                KilaueaDataEntity(
                    cacheKey = CacheKeys.SITUATION,
                    payloadJson = AppJson.encodeToString(KilaueaSituation.serializer(), toStore),
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    sourceUrl = "https://api.rootrecord.online/api/mobile/kilauea-situation",
                ),
            )
            situation?.takeIf { it.isActive() }
        }
    }

    suspend fun offlineFirst(forceRefresh: Boolean): Result<KilaueaSituation?> {
        if (!forceRefresh) {
            val row = dao.getByKey(CacheKeys.SITUATION)
            val cached = cached()
            if (cached != null && row != null) {
                val age = System.currentTimeMillis() - row.fetchedAtEpochMs
                if (age >= 0 && age < localMaxAgeMs) {
                    return Result.success(cached.takeIf { it.isActive() })
                }
            }
        }
        return refresh().recoverCatching { err ->
            cached()?.takeIf { it.isActive() } ?: throw err
        }
    }
}
