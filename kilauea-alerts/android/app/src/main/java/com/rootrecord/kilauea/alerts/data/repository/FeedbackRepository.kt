package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.BuildConfig
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val JsonMedia = "application/json; charset=utf-8".toMediaType()

/** Matches `POST /api/feedback` on api-kilauea.rootrecord.info (Worker `rootrecord-api-kilauea`; route copied from legacy primary). */
@Serializable
private data class FeedbackBody(
    val type: String = "general",
    val message: String,
    @SerialName("reply_email") val replyEmail: String? = null,
    @SerialName("include_diagnostics") val includeDiagnostics: Boolean = true,
    @SerialName("app_id") val appId: String = "rootrecord_kilauea_alerts_android",
)

@Singleton
class FeedbackRepository @Inject constructor(
    @param:Named("rootrecord") private val http: OkHttpClient,
) {

    suspend fun send(
        type: String,
        message: String,
        replyEmail: String?,
        includeDiagnostics: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = AppJson.encodeToString(
                FeedbackBody.serializer(),
                FeedbackBody(
                    type = type.trim().ifBlank { "general" }.take(80),
                    message = message.trim(),
                    replyEmail = replyEmail?.trim()?.takeIf { it.isNotEmpty() },
                    includeDiagnostics = includeDiagnostics,
                    appId = "rootrecord_kilauea_alerts_android",
                ),
            )
            val req = Request.Builder()
                .url("https://api.rootrecord.online/api/feedback")
                .post(bodyJson.toRequestBody(JsonMedia))
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return@runCatching
                val detail = runCatching {
                    AppJson.parseToJsonElement(text) as? JsonObject
                }.getOrNull()
                    ?.get("detail")
                    ?.jsonPrimitive
                    ?.content
                error(detail ?: "feedback_failed_${resp.code}")
            }
        }
    }
}
