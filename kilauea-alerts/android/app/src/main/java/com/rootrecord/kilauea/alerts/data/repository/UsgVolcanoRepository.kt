package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.local.KilaueaDataDao
import com.rootrecord.kilauea.alerts.data.local.KilaueaDataEntity
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val VNUM_KILAUEA = "332010"
private const val BASE = "https://volcanoes.usgs.gov/hans-public/api/volcano"

/**
 * Direct USGS HANS public API — no proxying.
 */
@Singleton
class UsgVolcanoRepository @Inject constructor(
    @param:Named("public") private val http: OkHttpClient,
    private val dao: KilaueaDataDao,
) {

    /** Matches Home screen poll interval — hero banner must not sit on stale alert/color for 15+ min. */
    private val localMaxAgeMs: Long = 2 * 60 * 1000L

    suspend fun cachedVolcanoJson(): String? = dao.getByKey(CacheKeys.VOLCANO_KILAUEA)?.payloadJson

    suspend fun refreshVolcanoStatus(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val volcano = runCatching { httpGet("$BASE/getVolcano/$VNUM_KILAUEA") }.getOrElse { "{}" }
            val newest = runCatching { httpGet("$BASE/newestForVolcano/$VNUM_KILAUEA") }.getOrElse { "{}" }
            val recent = runCatching { httpGet("$BASE/recentForVolcano/$VNUM_KILAUEA?limit=25") }.getOrElse { "[]" }

            val merged = buildJsonObject {
                put("volcano", runCatching { AppJson.parseToJsonElement(volcano) }.getOrElse { JsonPrimitive(volcano) })
                put("newest", runCatching { AppJson.parseToJsonElement(newest) }.getOrElse { JsonPrimitive(newest) })
                put(
                    "recent_raw",
                    try {
                        AppJson.parseToJsonElement(recent)
                    } catch (_: Exception) {
                        JsonArray(emptyList())
                    },
                )
                put("fetched_at_epoch_ms", JsonPrimitive(System.currentTimeMillis()))
            }

            dao.upsert(
                KilaueaDataEntity(
                    cacheKey = CacheKeys.VOLCANO_KILAUEA,
                    payloadJson = AppJson.encodeToString(JsonObject.serializer(), merged),
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    sourceUrl = "https://www.usgs.gov/volcanoes/kilauea",
                ),
            )

            val recentMessages = extractMessagesArray(recent)
            dao.upsert(
                KilaueaDataEntity(
                    cacheKey = CacheKeys.USGS_MESSAGES_RECENT,
                    payloadJson = AppJson.encodeToString(JsonElement.serializer(), recentMessages),
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    sourceUrl = BASE,
                ),
            )

            merged
        }
    }

    suspend fun offlineFirst(forceRefresh: Boolean): Result<JsonObject> {
        if (!forceRefresh) {
            val row = dao.getByKey(CacheKeys.VOLCANO_KILAUEA)
            val raw = row?.payloadJson
            if (raw != null) {
                val age = System.currentTimeMillis() - row.fetchedAtEpochMs
                if (age >= 0 && age < localMaxAgeMs) {
                    runCatching { AppJson.parseToJsonElement(raw) as JsonObject }.getOrNull()?.let {
                        return Result.success(it)
                    }
                }
            }
        }
        return refreshVolcanoStatus().recoverCatching { err ->
            cachedVolcanoJson()?.let { raw ->
                runCatching { AppJson.parseToJsonElement(raw) as JsonObject }.getOrNull()
            } ?: throw err
        }
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("usgs_http_${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    private fun extractMessagesArray(recentBody: String): JsonArray {
        val el = runCatching { AppJson.parseToJsonElement(recentBody) }.getOrNull() ?: return JsonArray(emptyList())
        return when (el) {
            is JsonArray -> el
            is JsonObject -> when (val v = el["messages"]) {
                is JsonArray -> v
                is JsonObject -> JsonArray(listOf(v))
                else -> JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }
    }
}
