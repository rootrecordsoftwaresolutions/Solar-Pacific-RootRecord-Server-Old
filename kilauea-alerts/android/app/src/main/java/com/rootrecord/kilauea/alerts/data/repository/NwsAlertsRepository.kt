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

private const val NWS_HI_ACTIVE =
    "https://api.weather.gov/alerts/active?area=HI&status=actual"

@Singleton
class NwsAlertsRepository @Inject constructor(
    @param:Named("public") private val http: OkHttpClient,
    private val dao: KilaueaDataDao,
) {

    suspend fun cachedJson(): String? = dao.getByKey(CacheKeys.NWS_ALERTS_HI)?.payloadJson

    suspend fun refresh(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = httpGet(NWS_HI_ACTIVE)
            AppJson.parseToJsonElement(body).let { it as JsonObject }
                .also { js ->
                    dao.upsert(
                        KilaueaDataEntity(
                            cacheKey = CacheKeys.NWS_ALERTS_HI,
                            payloadJson = AppJson.encodeToString(JsonObject.serializer(), js),
                            fetchedAtEpochMs = System.currentTimeMillis(),
                            sourceUrl = NWS_HI_ACTIVE,
                        ),
                    )
                }
        }
    }

    suspend fun offlineFirst(forceRefresh: Boolean): Result<JsonObject> {
        if (!forceRefresh) {
            cachedJson()?.let { raw ->
                runCatching { AppJson.parseToJsonElement(raw) as JsonObject }.getOrNull()?.let {
                    return Result.success(it)
                }
            }
        }
        return refresh()
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("nws_http_${resp.code}")
            resp.body?.string().orEmpty()
        }
    }
}
