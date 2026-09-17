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

@Singleton
class WeatherRepository @Inject constructor(
    private val api: RootRecordApi,
    private val dao: KilaueaDataDao,
) {

    /**
     * While under this age, serve Room cache only (no HTTP). After that, a non-forced fetch
     * still omits `?refresh=1` so the Worker can return D1-cached bundles and avoid Accu churn.
     */
    private val localDashboardMaxAgeMs: Long = 45 * 60 * 1000L

    suspend fun cachedDashboardJson(locationId: String): String? =
        dao.getByKey(CacheKeys.dashboard(locationId))?.payloadJson

    suspend fun refreshDashboard(
        locationId: String,
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = api.dashboard(
                lat = latitude,
                lon = longitude,
                locationId = locationId,
                refresh = forceRefresh.takeIf { it },
            )
            AppJson.parseToJsonElement(body).let {
                require(it is JsonObject) { "dashboard_not_object" }
                it
            }.also { json ->
                dao.upsert(
                    KilaueaDataEntity(
                        cacheKey = CacheKeys.dashboard(locationId),
                        payloadJson = AppJson.encodeToString(JsonObject.serializer(), json),
                        fetchedAtEpochMs = System.currentTimeMillis(),
                        sourceUrl = "https://api.rootrecord.online/api/dashboard",
                    ),
                )
            }
        }
    }

    suspend fun observeOfflineFirst(
        locationId: String,
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): Result<JsonObject> {
        if (!forceRefresh) {
            val row = dao.getByKey(CacheKeys.dashboard(locationId))
            val c = row?.payloadJson
            if (c != null) {
                val age = System.currentTimeMillis() - row.fetchedAtEpochMs
                if (age >= 0 && age < localDashboardMaxAgeMs) {
                    runCatching {
                        val el = AppJson.parseToJsonElement(c)
                        if (el is JsonObject) return Result.success(el)
                    }
                }
            }
        }
        return refreshDashboard(locationId, latitude, longitude, forceRefresh)
    }
}
