package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.remote.AppJson
import com.rootrecord.kilauea.alerts.data.remote.RootRecordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class DeveloperMessage(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String,
)

@Singleton
class DeveloperMessagesRepository @Inject constructor(
    private val api: RootRecordApi,
) {
    /**
     * Fetches the single most recent team update (the API returns at most one row). A
     * successful response with no row returns `Result.success(null)`. Network / parse
     * failures bubble up so callers can decide whether to hide the panel entirely.
     */
    suspend fun latest(): Result<DeveloperMessage?> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = api.developerMessages()
            val root = AppJson.parseToJsonElement(raw).jsonObject
            val list = root["messages"]?.jsonArray ?: return@runCatching null
            if (list.isEmpty()) return@runCatching null
            val r = list.first().jsonObject
            DeveloperMessage(
                id = r["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                title = r["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = r["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createdAt = r["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }
}
