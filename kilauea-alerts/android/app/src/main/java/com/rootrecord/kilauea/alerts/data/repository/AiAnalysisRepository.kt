package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.remote.AppJson
import com.rootrecord.kilauea.alerts.data.remote.RootRecordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class AiAnalysisReport(
    val id: String,
    val sourceType: String,
    val sourceTime: String,
    val severity: String,
    val event: String,
    val magnitude: Double?,
    val headline: String,
    val url: String,
    val freeText: String,
    val proText: String?,
    val proLocked: Boolean,
    val priorReportId: String?,
    val previousSummary: String?,
    val createdAt: String,
)

data class AiAnalysisCatalog(
    val reports: List<AiAnalysisReport>,
    val proUnlocked: Boolean,
)

@Singleton
class AiAnalysisRepository @Inject constructor(
    private val api: RootRecordApi,
) {
    suspend fun latest(limit: Int = 10): Result<AiAnalysisCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            val root = AppJson.parseToJsonElement(api.kilaueaAiAnalyses(limit)).jsonObject
            val reports = root["reports"]?.jsonArray.orEmpty().map { element ->
                val r = element.jsonObject
                AiAnalysisReport(
                    id = r.string("id"),
                    sourceType = r.string("source_type"),
                    sourceTime = r.string("source_time"),
                    severity = r.string("severity"),
                    event = r.string("event"),
                    magnitude = r["magnitude"]?.jsonPrimitive?.doubleOrNull,
                    headline = r.string("headline"),
                    url = r.string("url"),
                    freeText = r.string("free_text"),
                    proText = r["pro_text"]?.jsonPrimitive?.contentOrNull,
                    proLocked = r["pro_locked"]?.jsonPrimitive?.booleanOrNull == true,
                    priorReportId = r["prior_report_id"]?.jsonPrimitive?.contentOrNull,
                    previousSummary = r["previous_summary"]?.jsonPrimitive?.contentOrNull,
                    createdAt = r.string("created_at"),
                )
            }
            AiAnalysisCatalog(
                reports = reports,
                proUnlocked = root["pro_unlocked"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }
    }
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
