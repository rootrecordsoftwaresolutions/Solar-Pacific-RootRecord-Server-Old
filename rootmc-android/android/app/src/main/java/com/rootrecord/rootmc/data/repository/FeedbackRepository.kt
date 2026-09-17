package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.BuildConfig
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.di.ROOTRECORD_BLOCKNOTES_BASE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val JsonMedia = "application/json; charset=utf-8".toMediaType()

/** Matches `POST /api/feedback` on rootrecord-api-rootmc Worker. */
@Serializable
private data class FeedbackBody(
    val type: String = "general",
    val message: String,
    @SerialName("reply_email") val replyEmail: String? = null,
    @SerialName("include_diagnostics") val includeDiagnostics: Boolean = true,
    @SerialName("app_id") val appId: String = BLOCKNOTES_APP_ID,
)

const val BLOCKNOTES_APP_ID = "rootrecord_rootmc_android"

@Singleton
class FeedbackRepository @Inject constructor(
    @param:Named("rootrecord") private val http: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun send(
        type: String,
        message: String,
        replyEmail: String?,
        includeDiagnostics: Boolean,
    ): Result<Unit> = withContext(io) {
        runCatching {
            val bodyJson = AppJson.encodeToString(
                FeedbackBody.serializer(),
                FeedbackBody(
                    type = type.trim().ifBlank { "general" }.take(80),
                    message = message.trim(),
                    replyEmail = replyEmail?.trim()?.takeIf { it.isNotEmpty() },
                    includeDiagnostics = includeDiagnostics,
                    appId = BLOCKNOTES_APP_ID,
                ),
            )
            val req = Request.Builder()
                .url("${ROOTRECORD_BLOCKNOTES_BASE}api/feedback")
                .post(bodyJson.toRequestBody(JsonMedia))
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
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
