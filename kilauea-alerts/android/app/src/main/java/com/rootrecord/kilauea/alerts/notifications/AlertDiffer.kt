package com.rootrecord.kilauea.alerts.notifications

import com.rootrecord.kilauea.alerts.data.remote.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Computes newly-seen ids compared to persisted notify sets (stored as JSON string arrays in DataStore).
 */
object AlertDiffer {

    fun parseStringSet(json: String): Set<String> =
        runCatching {
            val el = AppJson.parseToJsonElement(json.trim())
            when (el) {
                is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
                else -> emptySet()
            }
        }.getOrElse { emptySet() }

    fun encodeStringSet(ids: Set<String>): String {
        val arr = JsonArray(ids.map { JsonPrimitive(it) })
        return AppJson.encodeToString(JsonElement.serializer(), arr)
    }

    /** NWS GeoJSON `features[].properties.id`. */
    fun nwsFeatureIds(geo: JsonObject): Set<String> {
        val feats = geo["features"] as? JsonArray ?: return emptySet()
        return feats.mapNotNull { f ->
            val props = (f as? JsonObject)?.get("properties") as? JsonObject ?: return@mapNotNull null
            (props["id"] as? JsonPrimitive)?.content
                ?: props["id"]?.toString()?.trim('"')
        }.toSet()
    }

    /**
     * Stable USGS notice id — must match server [volcanoNewestNoticeId] / FCM `notice_id`
     * so push dedupe and local poll do not re-fire the same alert when the app opens.
     */
    fun volcanoNewestId(volcanoBundle: JsonObject): String? {
        val newest = volcanoBundle["newest"] as? JsonObject ?: return null
        for (key in listOf("messageId", "id", "volcanoMessageId", "noticeId", "MessageId")) {
            (newest[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(220) }
        }
        val vm = newest["volcanoMessage"] as? JsonObject
        (vm?.get("messageId") as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }?.let { return it.take(220) }
        val sent = listOf("sentUtc", "SentUTC", "sent")
            .firstNotNullOfOrNull { key -> (newest[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() } }
            .orEmpty()
        val title = listOf("noticeTitle", "title", "Subject")
            .firstNotNullOfOrNull { key -> (newest[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() } }
            .orEmpty()
        if (sent.isNotEmpty() || title.isNotEmpty()) return "$sent|$title".take(220)
        return null
    }

    /**
     * Only treat earthquakes as alert candidates if origin time is within this window.
     * The USGS feed returns hundreds of past events; without this, lowering the magnitude
     * threshold or ID instability can look like many “new” historic quakes.
     */
    const val EQ_ALERT_RECENCY_MS: Long = 72L * 60 * 60 * 1000

    /**
     * Stable id for diffing: GeoJSON `Feature.id`, else first segment of `properties.ids`,
     * else `net`+`code`. Avoid raw [JsonElement.toString] on `code` (unstable formatting).
     */
    fun stableEarthquakeFeatureId(feature: JsonObject): String? {
        val topId = (feature["id"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
        if (topId != null) return topId
        val props = feature["properties"] as? JsonObject ?: return null
        val idsField = (props["ids"] as? JsonPrimitive)?.content
        if (!idsField.isNullOrBlank()) {
            val segment = idsField.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() }
            if (!segment.isNullOrBlank()) return segment
        }
        val net = (props["net"] as? JsonPrimitive)?.content?.trim()
        val code = (props["code"] as? JsonPrimitive)?.content?.trim()
        if (!net.isNullOrBlank() && !code.isNullOrBlank()) return "$net$code"
        if (!code.isNullOrBlank()) return code
        return null
    }

    /**
     * USGS FDSN GeoJSON event ids at or above [minMag].
     * When [minOriginTimeEpochMs] is set, drops older origins (milliseconds, USGS `properties.time`).
     */
    fun earthquakeEventIds(
        geo: JsonObject,
        minMag: Double,
        minOriginTimeEpochMs: Long? = null,
    ): Set<String> {
        val feats = geo["features"] as? JsonArray ?: return emptySet()
        return feats.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val props = o["properties"] as? JsonObject ?: return@mapNotNull null
            val mag = (props["mag"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            if (mag < minMag) return@mapNotNull null
            if (minOriginTimeEpochMs != null) {
                val timeMs = (props["time"] as? JsonPrimitive)?.content?.toLongOrNull()
                    ?: return@mapNotNull null
                if (timeMs < minOriginTimeEpochMs) return@mapNotNull null
            }
            stableEarthquakeFeatureId(o)
        }.filterNotNull().toSet()
    }

    /** Prefer the newest-by-origin-time id among [candidateIds] for notification copy. */
    fun newestEarthquakeIdAmong(geo: JsonObject, candidateIds: Set<String>): String? {
        if (candidateIds.isEmpty()) return null
        val feats = geo["features"] as? JsonArray ?: return candidateIds.firstOrNull()
        var bestTime = Long.MIN_VALUE
        var bestId: String? = null
        for (f in feats) {
            val o = f as? JsonObject ?: continue
            val id = stableEarthquakeFeatureId(o) ?: continue
            if (id !in candidateIds) continue
            val props = o["properties"] as? JsonObject ?: continue
            val timeMs = (props["time"] as? JsonPrimitive)?.content?.toLongOrNull() ?: Long.MIN_VALUE
            if (timeMs >= bestTime) {
                bestTime = timeMs
                bestId = id
            }
        }
        return bestId ?: candidateIds.firstOrNull()
    }

    /** One-line summary for notification (first matching feature). */
    fun earthquakeSummaryForEventId(geo: JsonObject, eventId: String): String? {
        val feats = geo["features"] as? JsonArray ?: return null
        for (f in feats) {
            val o = f as? JsonObject ?: continue
            if (stableEarthquakeFeatureId(o) != eventId) continue
            val props = o["properties"] as? JsonObject ?: continue
            val mag = (props["mag"] as? JsonPrimitive)?.content?.toDoubleOrNull()
            val place = (props["place"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            val mStr = mag?.let { "%.1f".format(it) } ?: "?"
            return if (place != null) "M $mStr — $place" else "M $mStr"
        }
        return null
    }

    fun newIds(current: Set<String>, seen: Set<String>): Set<String> = current - seen
}
