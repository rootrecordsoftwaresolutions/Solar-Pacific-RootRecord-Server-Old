package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.BuildConfig
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.di.ROOTRECORD_BLOCKNOTES_BASE
import com.rootrecord.rootmc.domain.usecase.WorldAiPayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val JsonMedia = "application/json; charset=utf-8".toMediaType()

data class WorldAiQuota(
    val tier: String,
    val usedToday: Int,
    val usedThisMonth: Int,
    val remainingToday: Int?,
    val remainingThisMonth: Int?,
    val dailyLimit: Int?,
    val monthlyLimit: Int?,
    val resetAt: String?,
)

data class WorldAiReport(
    val id: String,
    val worldKey: String,
    val worldName: String,
    val summaryText: String,
    val reportText: String,
    val createdAt: String,
)

data class WorldAiCatalog(
    val worldKey: String,
    val worldName: String,
    val reports: List<WorldAiReport>,
    val quota: WorldAiQuota,
    val proUnlocked: Boolean,
)

@Singleton
class WorldAiReportRepository @Inject constructor(
    @param:Named("rootrecord") private val http: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun fetchReports(worldKey: String, worldName: String): Result<WorldAiCatalog> =
        withContext(io) {
            runCatching {
                val url = "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/world-ai".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("world_key", worldKey)
                    .addQueryParameter("world_name", worldName)
                    .build()
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("X-App-Version", BuildConfig.VERSION_NAME)
                    .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                    .build()
                parseCatalog(http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw apiError(text, resp.code)
                    text
                })
            }
        }

    suspend fun fetchServerReports(serverId: String, serverName: String): Result<WorldAiCatalog> =
        withContext(io) {
            runCatching {
                val url = "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server-ai".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("server_id", serverId)
                    .addQueryParameter("server_name", serverName)
                    .build()
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("X-App-Version", BuildConfig.VERSION_NAME)
                    .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                    .build()
                parseCatalog(http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw apiError(text, resp.code)
                    text
                }, serverId = serverId)
            }
        }

    suspend fun generateServerReport(
        serverId: String,
        serverName: String,
        worldsPayload: List<WorldAiPayload>,
    ): Result<WorldAiCatalog> = withContext(io) {
        runCatching {
            val worldsJson = AppJson.encodeToString(worldsPayload)
            val body = buildString {
                append("{\"server_id\":")
                append(AppJson.encodeToString(kotlinx.serialization.serializer(), serverId))
                append(",\"server_name\":")
                append(AppJson.encodeToString(kotlinx.serialization.serializer(), serverName))
                append(",\"worlds_payload\":")
                append(worldsJson)
                append("}")
            }
            val req = Request.Builder()
                .url("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server-ai")
                .post(body.toRequestBody(JsonMedia))
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .build()
            parseCatalog(http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw apiError(text, resp.code)
                text
            }, serverId = serverId)
        }
    }

    suspend fun generateReport(
        worldKey: String,
        worldName: String,
        payload: WorldAiPayload,
    ): Result<WorldAiCatalog> = withContext(io) {
        runCatching {
            val body = buildString {
                append("{\"world_key\":")
                append(AppJson.encodeToString(kotlinx.serialization.serializer(), worldKey))
                append(",\"world_name\":")
                append(AppJson.encodeToString(kotlinx.serialization.serializer(), worldName))
                append(",\"world_payload\":")
                append(AppJson.encodeToString(WorldAiPayload.serializer(), payload))
                append("}")
            }
            val req = Request.Builder()
                .url("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/world-ai")
                .post(body.toRequestBody(JsonMedia))
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .build()
            parseCatalog(http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw apiError(text, resp.code)
                text
            })
        }
    }

    private fun parseCatalog(jsonText: String, serverId: String? = null): WorldAiCatalog {
        val root = AppJson.parseToJsonElement(jsonText).jsonObject
        val reports = root["reports"]?.jsonArray.orEmpty().map { parseReport(it.jsonObject) }
        val quotaObj = root["quota"]?.jsonObject
        val key = root.string("server_key").ifBlank { root.string("world_key") }
            .ifBlank { serverId?.let { "server:$it" }.orEmpty() }
        val name = root.string("server_name").ifBlank { root.string("world_name") }
        return WorldAiCatalog(
            worldKey = key,
            worldName = name,
            reports = reports,
            quota = WorldAiQuota(
                tier = quotaObj.string("tier"),
                usedToday = quotaObj?.get("used_today")?.jsonPrimitive?.intOrNull ?: 0,
                usedThisMonth = quotaObj?.get("used_this_month")?.jsonPrimitive?.intOrNull ?: 0,
                remainingToday = quotaObj?.get("remaining_today")?.jsonPrimitive?.intOrNull,
                remainingThisMonth = quotaObj?.get("remaining_this_month")?.jsonPrimitive?.intOrNull,
                dailyLimit = quotaObj?.get("daily_limit")?.jsonPrimitive?.intOrNull,
                monthlyLimit = quotaObj?.get("monthly_limit")?.jsonPrimitive?.intOrNull,
                resetAt = quotaObj?.get("reset_at")?.jsonPrimitive?.contentOrNull,
            ),
            proUnlocked = root["pro_unlocked"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun parseReport(obj: JsonObject): WorldAiReport = WorldAiReport(
        id = obj.string("id"),
        worldKey = obj.string("world_key"),
        worldName = obj.string("world_name"),
        summaryText = obj.string("summary_text"),
        reportText = obj.string("report_text"),
        createdAt = obj.string("created_at"),
    )

    private fun apiError(text: String, code: Int): Throwable {
        val root = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
        val detail = root?.get("detail")?.jsonPrimitive?.contentOrNull
        val message = root?.get("message")?.jsonPrimitive?.contentOrNull
        val msg = sanitizeGrokMessage(message ?: detail)
        return when {
            code == 401 -> IllegalStateException(
                msg ?: "Sign in to Root Record (More tab) to generate world AI reports.",
            )
            detail == "quota_exceeded" -> QuotaExceededException(msg ?: "Report quota exceeded.")
            detail == "world_data_empty" -> WorldDataEmptyException(
                msg ?: "Add notes, coordinates, or build plans before generating a report.",
            )
            detail == "ai_unavailable" -> AiUnavailableException(
                msg ?: "Grok is temporarily unavailable. Try again in a few minutes.",
            )
            detail == "server_data_empty" -> WorldDataEmptyException(
                msg ?: "Link your account and play on this server before generating a Grok report.",
            )
            detail == "world_payload_required" -> IllegalStateException(
                msg ?: "Could not send world data to the server.",
            )
            else -> IllegalStateException(msg ?: "grok_report_failed_$code")
        }
    }

    private fun sanitizeGrokMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.contains("x.ai", ignoreCase = true) || raw.contains("API key", ignoreCase = true)) {
            return "Grok is temporarily unavailable. Try again later."
        }
        if (raw.startsWith("HTTP ")) return "Grok is temporarily unavailable. Try again later."
        return raw
    }
}

class QuotaExceededException(message: String) : Exception(message)
class WorldDataEmptyException(message: String) : Exception(message)
class AiUnavailableException(message: String) : Exception(message)

private fun JsonObject?.string(key: String): String =
    this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
