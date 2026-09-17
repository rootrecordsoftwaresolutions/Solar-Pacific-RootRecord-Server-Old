package com.rootrecord.kilauea.alerts.ui.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shown when USGS data does not include enough official alert metadata to display the color/status pair. */
const val NO_HAZARDOUS_VOLCANO_ALERTS_MESSAGE = "No Hazardous Alerts Currently."

private val alertStatusToColor = mapOf(
    "NORMAL" to "GREEN",
    "ADVISORY" to "YELLOW",
    "WATCH" to "ORANGE",
    "WARNING" to "RED",
)

private val colorToAlertStatus = alertStatusToColor.entries.associate { (status, color) -> color to status }

/**
 * Maps merged USGS HANS JSON (`volcano` + `newest` siblings) to a short hero label.
 * Shows the official aviation color code and volcano alert status together when either is available.
 */
fun extractVolcanoAlertLevel(bundle: JsonObject): String {
    val volcano = bundle["volcano"]?.jsonObject
    val newest = bundle["newest"]?.jsonObject

    fun pickPrimitive(o: JsonObject?, keys: List<String>): String? {
        if (o == null) return null
        for (k in keys) {
            val v = o[k] as? JsonPrimitive ?: continue
            v.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    val alertKeys = listOf(
        "volcanoAlertLevel",
        "alertLevel",
        "currentVolcanoAlertLevel",
        "VolcanoAlertLevel",
    )
    val colorKeys = listOf(
        "colorCode",
        "aviationColorCode",
        "currentAviationColorCode",
        "aviationCode",
        "AviationColorCode",
    )

    var alertStatus = pickPrimitive(volcano, alertKeys)
        ?: pickPrimitive(newest, alertKeys)
    var aviationColor = pickPrimitive(volcano, colorKeys)
        ?: pickPrimitive(newest, colorKeys)

    newest?.get("noticeHtml")?.jsonPrimitive?.contentOrNull?.let { html ->
        val plain = stripHtmlTags(html)
        alertStatus = alertStatus ?: firstRegexToken(
            plain,
            listOf(
                "Current Volcano Alert Level:\\s*([A-Za-z]+)",
                "Volcano Alert Level:\\s*([A-Za-z]+)",
            ),
        )
        aviationColor = aviationColor ?: firstRegexToken(
            plain,
            listOf(
                "Current Aviation Color Code:\\s*([A-Za-z]+)",
                "Aviation Color Code:\\s*([A-Za-z]+)",
            ),
        )
    }

    officialAlertPairLabel(aviationColor, alertStatus)?.let { return it }

    volcano?.get("nvews_threat")?.jsonPrimitive?.contentOrNull?.let { return "Threat: $it" }

    return NO_HAZARDOUS_VOLCANO_ALERTS_MESSAGE
}

private fun firstRegexToken(plain: String, patterns: List<String>): String? {
    patterns.forEach { pattern ->
        Regex(pattern, RegexOption.IGNORE_CASE).find(plain)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    return null
}

private fun officialAlertPairLabel(aviationColor: String?, alertStatus: String?): String? {
    val color = normalizeOfficialToken(aviationColor)
    val status = normalizeOfficialToken(alertStatus)
    val resolvedColor = color ?: status?.let { alertStatusToColor[it] }
    val resolvedStatus = status ?: color?.let { colorToAlertStatus[it] }

    return when {
        resolvedColor != null && resolvedStatus != null -> "${resolvedColor.toTitleToken()} / ${resolvedStatus.toTitleToken()}"
        resolvedStatus != null -> resolvedStatus.toTitleToken()
        resolvedColor != null -> "${resolvedColor.toTitleToken()} / Aviation Color"
        else -> null
    }
}

private fun normalizeOfficialToken(value: String?): String? {
    val token = value
        ?.trim()
        ?.uppercase()
        ?.replace("_", " ")
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return when (token) {
        "N/A", "NA", "NONE", "NOT APPLICABLE", "NOT_APPLICABLE", "UNASSIGNED", "UNKNOWN" -> null
        else -> token
    }
}

private fun String.toTitleToken(): String =
    lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

/** One-line status from newest notice for Home subtitle. */
fun extractVolcanoHeroSubtitle(bundle: JsonObject): String? {
    val newest = bundle["newest"]?.jsonObject ?: return null
    val html = newest["noticeHtml"]?.jsonPrimitive?.contentOrNull ?: return null
    val plain = stripHtmlTags(html)
    val sentence = plain.split(". ").firstOrNull()?.trim()?.take(140) ?: plain.take(140)
    return sentence.takeIf { it.isNotBlank() }
}

/** Readable excerpt for Alerts tab (no raw JsonObject.toString()). */
fun formatUsgsNewestForAlerts(newest: JsonElement?): String {
    when (newest) {
        null -> return "—"
        is JsonObject -> {
            val html = newest["noticeHtml"]?.jsonPrimitive?.contentOrNull
            if (html != null) {
                val plain = stripHtmlTags(html)
                return plain.take(1800).trim()
            }
            val title = newest["title"]?.jsonPrimitive?.contentOrNull
                ?: newest["noticeTitle"]?.jsonPrimitive?.contentOrNull
            val body = newest["noticeText"]?.jsonPrimitive?.contentOrNull
                ?: newest["text"]?.jsonPrimitive?.contentOrNull
            return listOfNotNull(title, body?.take(1200)).joinToString("\n\n").ifBlank { "—" }
        }
        is JsonPrimitive -> {
            val raw = newest.contentOrNull ?: return "—"
            return stripHtmlTags(raw).take(1800).trim().ifBlank { "—" }
        }
        else -> return "—"
    }
}
