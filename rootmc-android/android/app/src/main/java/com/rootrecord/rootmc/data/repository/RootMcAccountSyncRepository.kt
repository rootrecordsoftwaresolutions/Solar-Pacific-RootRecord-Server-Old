package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.data.sync.RootMcCloudSnapshot
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.di.ROOTRECORD_BLOCKNOTES_BASE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val JsonMedia = "application/json; charset=utf-8".toMediaType()
private val SNAPSHOT_GET_URL = "${ROOTRECORD_BLOCKNOTES_BASE}api/sync/snapshot"
private val SNAPSHOT_PUT_URL = SNAPSHOT_GET_URL

data class RemoteAccountSnapshot(
    val updatedAt: Long,
    val schemaVersion: Int,
    val snapshot: RootMcCloudSnapshot,
)

@Singleton
class RootMcAccountSyncRepository @Inject constructor(
    @param:Named("rootrecord") private val http: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun fetchRemoteSnapshot(): Result<RemoteAccountSnapshot?> = withContext(io) {
        runCatching {
            val req = Request.Builder()
                .url(SNAPSHOT_GET_URL)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error(parseErrorDetail(text) ?: "sync_fetch_${resp.code}")
                }
                val root = AppJson.parseToJsonElement(text).jsonObject
                if (root["empty"]?.jsonPrimitive?.booleanOrNull == true) return@runCatching null
                val updatedAt = root["updated_at"]?.jsonPrimitive?.longOrNull ?: 0L
                val schemaVersion = root["schema_version"]?.jsonPrimitive?.longOrNull?.toInt() ?: 1
                val snapEl = root["snapshot"] ?: error("missing_snapshot")
                val snapshot = AppJson.decodeFromJsonElement(RootMcCloudSnapshot.serializer(), snapEl)
                RemoteAccountSnapshot(
                    updatedAt = updatedAt,
                    schemaVersion = schemaVersion,
                    snapshot = snapshot,
                )
            }
        }
    }

    suspend fun pushSnapshot(snapshot: RootMcCloudSnapshot): Result<Long> = withContext(io) {
        runCatching {
            val payload = buildString {
                append("{")
                append("\"schema_version\":")
                append(RootMcCloudSnapshot.SCHEMA_VERSION)
                append(",\"exported_at\":")
                append(snapshot.exportedAt)
                append(",\"snapshot\":")
                append(AppJson.encodeToString(RootMcCloudSnapshot.serializer(), snapshot))
                append("}")
            }
            val req = Request.Builder()
                .url(SNAPSHOT_PUT_URL)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .put(payload.toRequestBody(JsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error(parseErrorDetail(text) ?: "sync_push_${resp.code}")
                }
                val root = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
                root?.get("updated_at")?.jsonPrimitive?.longOrNull ?: snapshot.exportedAt
            }
        }
    }

    private fun parseErrorDetail(text: String): String? {
        if (text.isBlank()) return null
        return runCatching {
            AppJson.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
    }
}
