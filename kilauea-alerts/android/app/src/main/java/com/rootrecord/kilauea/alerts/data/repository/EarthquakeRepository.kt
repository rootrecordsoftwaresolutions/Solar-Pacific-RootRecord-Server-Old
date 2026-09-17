package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.local.KilaueaDataDao
import com.rootrecord.kilauea.alerts.data.local.KilaueaDataEntity
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val HAWAII_BBOX_URL =
    "https://earthquake.usgs.gov/fdsnws/event/1/query?" +
        "format=geojson&orderby=time&limit=300&" +
        "minlatitude=18.8&maxlatitude=22.6&minlongitude=-161.0&maxlongitude=-154.5"

@Singleton
class EarthquakeRepository @Inject constructor(
    @param:Named("public") private val http: OkHttpClient,
    private val dao: KilaueaDataDao,
) {

    suspend fun cachedGeoJson(): String? = dao.getByKey(CacheKeys.EARTHQUAKES_FDSN)?.payloadJson

    suspend fun refreshEarthquakes(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = httpGet(HAWAII_BBOX_URL)
            AppJson.parseToJsonElement(body).let { it as JsonObject }
                .also { fc ->
                    dao.upsert(
                        KilaueaDataEntity(
                            cacheKey = CacheKeys.EARTHQUAKES_FDSN,
                            payloadJson = AppJson.encodeToString(JsonObject.serializer(), fc),
                            fetchedAtEpochMs = System.currentTimeMillis(),
                            sourceUrl = HAWAII_BBOX_URL,
                        ),
                    )
                }
        }
    }

    suspend fun offlineFirst(forceRefresh: Boolean): Result<JsonObject> {
        if (!forceRefresh) {
            cachedGeoJson()?.let { raw ->
                runCatching { AppJson.parseToJsonElement(raw) as JsonObject }.getOrNull()?.let {
                    return Result.success(it)
                }
            }
        }
        return refreshEarthquakes()
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("fdsn_http_${resp.code}")
            resp.body?.string().orEmpty()
        }
    }
}
