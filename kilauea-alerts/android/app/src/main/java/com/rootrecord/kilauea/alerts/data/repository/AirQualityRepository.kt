package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.local.KilaueaDataDao
import com.rootrecord.kilauea.alerts.data.local.KilaueaDataEntity
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import com.rootrecord.kilauea.alerts.data.remote.RootRecordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Open-Meteo air quality via `GET /api/air-quality/current?lat&lon` (same source as Weather Manager). */
@Singleton
class AirQualityRepository @Inject constructor(
    private val api: RootRecordApi,
    private val dao: KilaueaDataDao,
) {
    private val localMaxAgeMs: Long = 30 * 60 * 1000L

    suspend fun offlineFirst(locationId: String, lat: Double, lon: Double, forceRefresh: Boolean): Result<JsonObject> {
        if (!forceRefresh) {
            val row = dao.getByKey(CacheKeys.airQuality(locationId))
            val raw = row?.payloadJson
            if (raw != null) {
                val age = System.currentTimeMillis() - row.fetchedAtEpochMs
                if (age >= 0 && age < localMaxAgeMs) {
                    runCatching {
                        val el = AppJson.parseToJsonElement(raw)
                        if (el is JsonObject) return Result.success(el)
                    }
                }
            }
        }
        return refresh(locationId, lat, lon)
    }

    private suspend fun refresh(locationId: String, lat: Double, lon: Double): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = api.airQualityCurrent(lat = lat, lon = lon)
                AppJson.parseToJsonElement(body).let { it as JsonObject }
                    .also { js ->
                        dao.upsert(
                            KilaueaDataEntity(
                                cacheKey = CacheKeys.airQuality(locationId),
                                payloadJson = AppJson.encodeToString(JsonObject.serializer(), js),
                                fetchedAtEpochMs = System.currentTimeMillis(),
                                sourceUrl = "/api/air-quality/current",
                            ),
                        )
                    }
            }
        }
}
