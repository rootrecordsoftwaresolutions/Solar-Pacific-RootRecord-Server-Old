package com.rootrecord.avaops.ops

import com.rootrecord.avaops.ui.data.QuickStatusItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parsed shape of GET /api/ops/mobile-dashboard — mirrors apps/core/routes/ops.py. */
data class AvaBoardSnapshot(
    val ok: Boolean,
    val generatedAt: String?,
    val weather: WeatherInfo?,
    val kilauea: KilaueaInfo?,
    val power: PowerInfo?,
    val host: HostInfo?,
    val minecraft: MinecraftLiveInfo?,
    val inbox: InboxInfo?,
    val media: MediaInfo?,
    val mysql: MysqlInfo?,
    val quakes: QuakesInfo?,
    val reports: ReportsInfo?,
    val tunnel: TunnelInfo? = null,
    val procs: ProcsInfo? = null,
    val origin: OriginInfo? = null,
    val ollama: OllamaInfo? = null,
    val servers: List<OpsServer> = emptyList(),
)

data class WeatherInfo(
    val ok: Boolean,
    val period: String?,
    val temperatureF: String?,
    val forecast: String?,
    val alertsActive: Int?,
    val updatedAt: String?,
    val detail: String?,
)

data class KilaueaInfo(
    val ok: Boolean,
    val alertLevel: String?,
    val multiplier: Double?,
    val eventsNearby: Int?,
    val maxMagnitude: Double?,
    val updatedAt: String?,
    val detail: String?,
    val source: String? = null,
)

data class PowerDevice(
    val label: String,
    val soc: Int?,
    val online: Boolean,
    val pvW: Double?,
    val ebattW: Double?,
    val acOutW: Double?,
    val inputKind: String? = null,
)

data class PowerInfo(
    val ok: Boolean,
    val live: Boolean,
    val source: String?,
    val batteryPct: Double?,
    val solarInW: Double?,
    val ebattInW: Double?,
    val loadW: Double?,
    val state: String?,
    val devices: List<PowerDevice>,
    val detail: String?,
    val generator: Boolean? = null,
    val generatorCard: String? = null,
    val acInW: Double? = null,
    val overCeiling: Boolean? = null,
    val bleGone: Boolean? = null,
)

data class HostInfo(
    val cpuPct: Double?,
    val memPct: Double?,
    val memUsedGb: Double?,
    val memTotalGb: Double?,
    val tempC: Double?,
    val hostBatteryPct: Int?,
    val diskPct: Double?,
    val gpuName: String?,
    val hostBatteryPlugged: Boolean? = null,
)

data class MinecraftLiveInfo(
    val ok: Boolean,
    val liveOnline: Boolean,
    val liveLatencyMs: Int?,
    val liveHost: String?,
    val livePort: Int?,
    val testOnline: Boolean,
    val testLatencyMs: Int?,
    val jar: String?,
    val plugins: Int?,
    val dirPresent: Boolean,
)

data class InboxInfo(
    val discordTokenSet: Boolean,
    val reportSubscribers: Int,
    val inboxFile: Boolean,
)

data class MediaFolderInfo(
    val exists: Boolean,
    val path: String?,
    val typeCount: Int,
    val typeNames: List<String> = emptyList(),
)

data class MediaInfo(
    val public: MediaFolderInfo?,
    val private: MediaFolderInfo?,
)

data class MysqlInfo(
    val live: Boolean,
    val local3306: Boolean,
    val shockbyte: Boolean,
)

data class QuakeEvent(
    val magnitude: Double?,
    val place: String?,
    val timeMs: Long?,
    val id: String?,
    val sig: Int?,
)

data class QuakesInfo(
    val global: List<QuakeEvent>,
    val island: List<QuakeEvent>,
    val fetchedAt: String?,
)

data class ReportWindow(
    val id: String,
    val label: String,
    val whenLabel: String?,
    val done: Boolean,
    val status: String,
)

data class ReportsInfo(
    val currentExists: Boolean,
    val currentName: String?,
    val currentMtimeMs: Long?,
    val dueToday: List<ReportWindow>,
    val hstDay: String? = null,
    val freshnessOk: Boolean? = null,
)

data class TunnelInfo(
    val processCount: Int?,
    val metricsOk: Boolean,
    val connections: Int?,
    val note: String?,
)

data class ProcsInfo(
    val uvicornN: Int?,
    val electronN: Int?,
)

data class OriginInfo(
    val uptimeS: Int?,
    val deskUptimeS: Int?,
    val lastReturnAt: String?,
)

data class OllamaInfo(
    val up: Boolean,
    val models: List<String> = emptyList(),
)

fun AvaBoardSnapshot.quickStatusTiles(): List<QuickStatusItem> = listOf(
    QuickStatusItem(
        id = "weather",
        title = "Weather & NWS Hawaii",
        status = weather?.temperatureF?.let { "$it°F" }
            ?: weather?.period
            ?: if (weather?.ok == true) "Live" else "No data",
        healthy = weather?.ok == true && (weather.alertsActive ?: 0) == 0,
    ),
    QuickStatusItem(
        id = "volcano",
        title = "Kīlauea Monitor",
        status = kilauea?.alertLevel?.replaceFirstChar { it.uppercase() } ?: if (kilauea?.ok == true) "Live" else "No data",
        healthy = kilauea?.ok == true && (kilauea.alertLevel ?: "normal").lowercase() in setOf("normal", "green", "watch"),
    ),
    QuickStatusItem(
        id = "earthquake",
        title = "Earthquake Desk",
        status = quakes?.island?.size?.let { "$it island" } ?: "No data",
        healthy = quakes != null,
    ),
    QuickStatusItem(
        id = "power",
        title = "EcoFlow / Power",
        status = when {
            power?.overCeiling == true -> "GENERATOR FAULT"
            power?.generator == true -> "GENERATOR ON"
            power?.bleGone == true -> "BLE unknown"
            power?.batteryPct != null -> "SOC ${power.batteryPct.toInt()}%"
            power?.ok == true -> "Live"
            else -> "No data"
        },
        healthy = power?.ok == true && power.overCeiling != true && (power.batteryPct ?: 100.0) >= 20,
    ),
    QuickStatusItem(
        id = "reports",
        title = "Reports Board",
        status = when {
            reports?.currentExists == true -> reports.currentName ?: "Current on disk"
            reports != null -> "No current file"
            else -> "No data"
        },
        healthy = reports?.currentExists == true && reports.freshnessOk != false,
    ),
    QuickStatusItem(
        id = "media",
        title = "OBS & Media",
        status = when {
            media?.public?.exists == true -> "${media.public.typeCount} public types"
            media != null -> "Folders missing"
            else -> "No data"
        },
        healthy = media?.public?.exists == true,
    ),
    QuickStatusItem(
        id = "messaging",
        title = "Messaging",
        status = inbox?.let { "${it.reportSubscribers} subs" } ?: "No data",
        healthy = inbox?.discordTokenSet == true,
    ),
    QuickStatusItem(
        id = "minecraft",
        title = "Minecraft Live",
        status = when {
            minecraft == null -> "No data"
            minecraft.liveOnline -> minecraft.liveLatencyMs?.let { "Live ${it}ms" } ?: "Live up"
            else -> "Live down"
        },
        healthy = minecraft?.liveOnline == true,
    ),
    QuickStatusItem(
        id = "metrics",
        title = "System Metrics",
        status = host?.cpuPct?.let { "CPU ${it.toInt()}%" } ?: "No data",
        healthy = host != null,
    ),
)

fun fallbackQuickStatus(): List<QuickStatusItem> = listOf(
    QuickStatusItem("weather", "Weather & NWS Hawaii", "Waiting", false),
    QuickStatusItem("volcano", "Kīlauea Monitor", "Waiting", false),
    QuickStatusItem("earthquake", "Earthquake Desk", "Waiting", false),
    QuickStatusItem("power", "EcoFlow / Power", "Waiting", false),
    QuickStatusItem("reports", "Reports Board", "Waiting", false),
    QuickStatusItem("media", "OBS & Media", "Waiting", false),
    QuickStatusItem("messaging", "Messaging", "Waiting", false),
    QuickStatusItem("minecraft", "Minecraft Live", "Waiting", false),
    QuickStatusItem("metrics", "System Metrics", "Waiting", false),
)

fun formatWatts(value: Double?): String =
    value?.let { "${if (it % 1.0 == 0.0) it.toInt() else String.format(Locale.US, "%.0f", it)} W" } ?: "—"

fun formatPct(value: Number?): String = value?.let { "${it.toInt()}%" } ?: "—"

fun formatUptime(seconds: Int?): String {
    if (seconds == null || seconds < 0) return "—"
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3_600
    val m = (seconds % 3_600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        append("${h}h ${m}m")
    }.trim()
}

fun formatEpochMs(ms: Long?, zoneId: String = "Pacific/Honolulu"): String {
    if (ms == null || ms <= 0L) return "—"
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone(zoneId)
    return fmt.format(Date(ms))
}

fun formatIso(stamp: String?): String {
    if (stamp.isNullOrBlank()) return "—"
    return stamp.replace("T", " ").removeSuffix("Z")
}
