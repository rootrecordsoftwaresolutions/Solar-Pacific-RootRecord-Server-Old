package com.rootrecord.avaops.ops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fetches GET /api/ops/mobile-dashboard, trying each configured base URL in order. */
class BoardApiClient(
    private val baseUrls: List<String>,
    private val accessToken: String? = null,
) {
    suspend fun fetchDashboard(): AvaBoardSnapshot = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (base in baseUrls.map { it.trim() }.filter { it.isUsableHttpBase() }) {
            try {
                val json = getJson(base.trimEnd('/') + "/api/ops/mobile-dashboard")
                return@withContext json.toBoardSnapshot()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no_base_url_configured")
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            accessToken?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else (connection.errorStream ?: connection.inputStream)
            val payload = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("board_http_$code")
            return JSONObject(payload)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun String.isUsableHttpBase(): Boolean {
    if (isBlank()) return false
    // Stale README host — not a RootRecord public surface; reject prefs/BuildConfig too.
    if (contains("ops.rootrecord.online", ignoreCase = true)) return false
    // Debug BuildConfig uses emulator loopback. A real phone never reaches the OmniBook that way.
    if (contains("10.0.2.2") && !isAndroidEmulator()) return false
    return true
}

private fun isAndroidEmulator(): Boolean {
    val fingerprint = android.os.Build.FINGERPRINT.lowercase()
    val model = android.os.Build.MODEL.lowercase()
    val product = android.os.Build.PRODUCT.lowercase()
    return fingerprint.contains("generic") ||
        model.contains("emulator") ||
        model.contains("android sdk") ||
        product.contains("sdk") ||
        product.contains("emulator")
}

internal fun JSONObject.toBoardSnapshot(): AvaBoardSnapshot = AvaBoardSnapshot(
    ok = if (has("board_ok") && !isNull("board_ok")) optBoolean("board_ok") else optBoolean("ok", false),
    generatedAt = optString("generated_at").takeIf { it.isNotBlank() },
    weather = optJSONObject("weather")?.toWeatherInfo(),
    kilauea = optJSONObject("kilauea")?.toKilaueaInfo(),
    power = optJSONObject("power")?.toPowerInfo(),
    host = optJSONObject("host")?.toHostInfo(),
    minecraft = optJSONObject("minecraft")?.toMinecraftInfo(),
    inbox = optJSONObject("inbox")?.toInboxInfo(),
    media = optJSONObject("media")?.toMediaInfo(),
    mysql = optJSONObject("mysql")?.toMysqlInfo(),
    quakes = optJSONObject("quakes")?.toQuakesInfo(),
    reports = optJSONObject("reports")?.toReportsInfo(),
    tunnel = optJSONObject("tunnel")?.toTunnelInfo(),
    procs = optJSONObject("procs")?.toProcsInfo(),
    origin = optJSONObject("origin")?.toOriginInfo(),
    ollama = optJSONObject("ollama")?.toOllamaInfo(),
    servers = toOpsServerList(),
)

private fun JSONObject.toOpsServerList(): List<OpsServer> {
    val arr = when (val raw = opt("servers")) {
        is JSONArray -> raw
        is JSONObject -> raw.optJSONArray("servers") ?: JSONArray()
        else -> JSONArray()
    }
    val out = mutableListOf<OpsServer>()
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
        out.add(
            OpsServer(
                id = id,
                name = item.optString("name", id),
                provider = ProviderKind.ROOTRECORD,
                status = item.optString("status").toBoardServerStatus(),
                playersOnline = item.optIntOrNull("players_online"),
                playerCapacity = item.optIntOrNull("player_capacity"),
                address = item.optString("address").takeIf { it.isNotBlank() },
            ),
        )
    }
    return out
}

private fun String.toBoardServerStatus(): ServerStatus = when (lowercase()) {
    "online", "running" -> ServerStatus.ONLINE
    "offline", "stopped" -> ServerStatus.OFFLINE
    "starting", "restarting" -> ServerStatus.STARTING
    else -> ServerStatus.UNKNOWN
}

private fun JSONObject.toWeatherInfo() = WeatherInfo(
    ok = optBoolean("ok", false),
    period = optString("period").takeIf { it.isNotBlank() },
    temperatureF = optDisplayString("temperature_f"),
    forecast = optString("forecast").takeIf { it.isNotBlank() },
    alertsActive = optIntOrNull("alerts_active"),
    updatedAt = optString("updated_at").takeIf { it.isNotBlank() },
    detail = optString("detail").takeIf { it.isNotBlank() },
)

private fun JSONObject.toKilaueaInfo() = KilaueaInfo(
    ok = optBoolean("ok", false),
    alertLevel = optString("alert_level").takeIf { it.isNotBlank() },
    multiplier = optDoubleOrNull("multiplier"),
    eventsNearby = optIntOrNull("events_nearby"),
    maxMagnitude = optDoubleOrNull("max_magnitude"),
    updatedAt = optString("updated_at").takeIf { it.isNotBlank() },
    detail = optString("detail").takeIf { it.isNotBlank() },
    source = optString("source").takeIf { it.isNotBlank() },
)

private fun JSONObject.toPowerInfo(): PowerInfo {
    val devices = mutableListOf<PowerDevice>()
    optJSONArray("devices")?.let { arr ->
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            devices.add(
                PowerDevice(
                    label = d.optString("label", "pack"),
                    soc = d.optIntOrNull("soc"),
                    online = d.optBoolean("online", false),
                    pvW = d.optDoubleOrNull("pv_w"),
                    ebattW = d.optDoubleOrNull("ebatt_w"),
                    acOutW = d.optDoubleOrNull("ac_out_w"),
                    inputKind = d.optString("input_kind").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
    return PowerInfo(
        ok = optBoolean("ok", false),
        live = optBoolean("live", false),
        source = optString("source").takeIf { it.isNotBlank() },
        batteryPct = optDoubleOrNull("battery_pct"),
        solarInW = optDoubleOrNull("solar_in_w"),
        ebattInW = optDoubleOrNull("ebatt_in_w"),
        loadW = optDoubleOrNull("load_w"),
        state = optString("state").takeIf { it.isNotBlank() },
        devices = devices,
        detail = optString("detail").takeIf { it.isNotBlank() },
        generator = if (has("generator") && !isNull("generator")) optBoolean("generator") else null,
        generatorCard = optString("generator_card").takeIf { it.isNotBlank() },
        acInW = optDoubleOrNull("ac_in_w"),
        overCeiling = if (has("over_ceiling") && !isNull("over_ceiling")) optBoolean("over_ceiling") else null,
        bleGone = if (has("ble_gone") && !isNull("ble_gone")) optBoolean("ble_gone") else null,
    )
}

private fun JSONObject.toHostInfo() = HostInfo(
    cpuPct = optDoubleOrNull("cpu_pct"),
    memPct = optDoubleOrNull("mem_pct"),
    memUsedGb = optDoubleOrNull("mem_used_gb"),
    memTotalGb = optDoubleOrNull("mem_total_gb"),
    tempC = optDoubleOrNull("temp_c"),
    hostBatteryPct = optIntOrNull("host_battery_pct"),
    diskPct = optDoubleOrNull("disk_pct"),
    gpuName = optString("gpu_name").takeIf { it.isNotBlank() },
    hostBatteryPlugged = if (has("host_battery_plugged") && !isNull("host_battery_plugged")) optBoolean("host_battery_plugged") else null,
)

private fun JSONObject.toMinecraftInfo(): MinecraftLiveInfo {
    val live = optJSONObject("live") ?: JSONObject()
    val test = optJSONObject("test") ?: JSONObject()
    return MinecraftLiveInfo(
        ok = optBoolean("ok", false),
        liveOnline = live.optBoolean("online", false),
        liveLatencyMs = live.optIntOrNull("latency_ms"),
        liveHost = live.optString("host").takeIf { it.isNotBlank() },
        livePort = live.optIntOrNull("port"),
        testOnline = test.optBoolean("online", false),
        testLatencyMs = test.optIntOrNull("latency_ms"),
        jar = optString("jar").takeIf { it.isNotBlank() },
        plugins = optIntOrNull("plugins"),
        dirPresent = optBoolean("dir_present", false) || optBoolean("dirPresent", false),
    )
}

private fun JSONObject.toInboxInfo() = InboxInfo(
    discordTokenSet = optBoolean("discord_token_set", false),
    reportSubscribers = optInt("report_subscribers", 0),
    inboxFile = optBoolean("inbox_file", false),
)

private fun JSONObject.toMediaFolderInfo(): MediaFolderInfo {
    val types = optJSONArray("types") ?: JSONArray()
    val names = mutableListOf<String>()
    for (i in 0 until types.length()) {
        val item = types.optJSONObject(i)
        val name = item?.optString("name")?.takeIf { it.isNotBlank() }
            ?: types.optString(i).takeIf { it.isNotBlank() }
        if (name != null) names.add(name)
    }
    return MediaFolderInfo(
        exists = optBoolean("exists", false),
        path = optString("path").takeIf { it.isNotBlank() },
        typeCount = names.size,
        typeNames = names,
    )
}

private fun JSONObject.toMediaInfo() = MediaInfo(
    public = optJSONObject("public")?.toMediaFolderInfo(),
    private = optJSONObject("private")?.toMediaFolderInfo(),
)

private fun JSONObject.toMysqlInfo() = MysqlInfo(
    live = optBoolean("live", false),
    local3306 = optBoolean("local_3306", false),
    shockbyte = optBoolean("shockbyte", false),
)

private fun JSONObject.toQuakeList(key: String): List<QuakeEvent> {
    val arr = optJSONArray(key) ?: JSONArray()
    val out = mutableListOf<QuakeEvent>()
    for (i in 0 until arr.length()) {
        val q = arr.optJSONObject(i) ?: continue
        out.add(
            QuakeEvent(
                magnitude = q.optDoubleOrNull("mag"),
                place = q.optString("place").takeIf { it.isNotBlank() },
                timeMs = q.optLongOrNull("time"),
                id = q.optString("id").takeIf { it.isNotBlank() },
                sig = q.optIntOrNull("sig"),
            ),
        )
    }
    return out
}

private fun JSONObject.toQuakesInfo() = QuakesInfo(
    global = toQuakeList("global"),
    island = toQuakeList("island"),
    fetchedAt = optString("fetched_at").takeIf { it.isNotBlank() } ?: optString("ts").takeIf { it.isNotBlank() },
)

private fun JSONObject.toReportsInfo(): ReportsInfo {
    val current = optJSONObject("current") ?: JSONObject()
    val dueArr = optJSONArray("due_today") ?: optJSONArray("dueToday")
    val due = mutableListOf<ReportWindow>()
    dueArr?.let { arr ->
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            due.add(
                ReportWindow(
                    id = row.optString("id"),
                    label = row.optString("label", row.optString("id")),
                    whenLabel = row.optString("when").takeIf { it.isNotBlank() },
                    done = row.optBoolean("done", false),
                    status = row.optString("status", "upcoming"),
                ),
            )
        }
    }
    val freshness = optJSONObject("freshness")
    return ReportsInfo(
        currentExists = current.optBoolean("exists", false),
        currentName = current.optString("name").takeIf { it.isNotBlank() },
        currentMtimeMs = current.optLongOrNull("mtimeMs"),
        dueToday = due,
        hstDay = optString("hstDay").takeIf { it.isNotBlank() },
        freshnessOk = freshness?.optBoolean("ok"),
    )
}

private fun JSONObject.toTunnelInfo() = TunnelInfo(
    processCount = optIntOrNull("process_count"),
    metricsOk = optBoolean("metrics_ok", false),
    connections = optIntOrNull("connections"),
    note = optString("note").takeIf { it.isNotBlank() },
)

private fun JSONObject.toProcsInfo() = ProcsInfo(
    uvicornN = optIntOrNull("uvicorn_n"),
    electronN = optIntOrNull("electron_n"),
)

private fun JSONObject.toOriginInfo() = OriginInfo(
    uptimeS = optIntOrNull("uptime_s"),
    deskUptimeS = optIntOrNull("desk_uptime_s"),
    lastReturnAt = optString("last_return_at").takeIf { it.isNotBlank() },
)

private fun JSONObject.toOllamaInfo(): OllamaInfo {
    val models = mutableListOf<String>()
    optJSONArray("models")?.let { arr ->
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            val name = item?.optString("name")?.takeIf { it.isNotBlank() }
                ?: arr.optString(i).takeIf { it.isNotBlank() }
            if (name != null) models.add(name)
        }
    }
    return OllamaInfo(up = optBoolean("up", false), models = models)
}

private fun JSONObject.optDisplayString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return when (val raw = opt(name)) {
        is String -> raw.takeIf { it.isNotBlank() }
        is Number -> raw.toString()
        else -> optString(name).takeIf { it.isNotBlank() }
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
private fun JSONObject.optDoubleOrNull(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    val raw = opt(name) ?: return null
    return when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull()
        else -> null
    }
}
