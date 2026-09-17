package com.rootrecord.kilauea.alerts.notifications

import com.rootrecord.kilauea.alerts.ui.util.stripHtmlTags
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Heuristics on USGS HANS newest notice text + volcano metadata.
 * Not a substitute for official statements — speeds up user awareness when wording matches activity.
 */
object VolcanoNoticeSignals {

    fun newestNoticePlainText(bundle: JsonObject): String {
        val newest = bundle["newest"]?.jsonObject ?: return ""
        val html = newest["noticeHtml"]?.jsonPrimitive?.contentOrNull ?: return ""
        return stripHtmlTags(html)
    }

    /** Aviation color codes when USGS elevates aviation risk — often aligned with eruptive unrest. */
    fun aviationColorElevated(bundle: JsonObject): Boolean {
        val code = bundle["volcano"]?.jsonObject?.get("colorCode")?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.uppercase()
            ?: return false
        return code == "RED" || code == "ORANGE"
    }

    /**
     * True when plain-language notice likely describes eruptive lava activity or similar unrest,
     * excluding common "no eruption" style phrases.
     */
    fun suggestsEruptiveActivity(plain: String): Boolean {
        if (plain.isBlank()) return false
        val t = plain.lowercase()
        if (NEGATION_PHRASES.any { t.contains(it) }) return false
        return ERUPTION_SIGNAL_PHRASES.any { t.contains(it) }
    }

    fun shouldUseUrgentVolcanoNotification(bundle: JsonObject, plainNotice: String): Boolean =
        suggestsEruptiveActivity(plainNotice) || aviationColorElevated(bundle)

    private val NEGATION_PHRASES = listOf(
        "no active eruption",
        "not erupting",
        "no eruption",
        "eruption has ended",
        "eruption ended",
        "not currently erupting",
        "remains paused",
        "activity paused",
        "has ceased",
        "ceased erupting",
    )

    /** Deliberately specific — avoid bare "lava" / "vent" alone (too many benign notices). */
    private val ERUPTION_SIGNAL_PHRASES = listOf(
        "eruption",
        "eruptive",
        "erupting",
        "lava fountain",
        "lava lake",
        "lava flow",
        "lava flows",
        "active lava",
        "spattering",
        "lava spatter",
        "effusive",
        "effusion",
        "resumption of",
        "outbreak",
        "new vent",
        "lava visible",
    )
}
