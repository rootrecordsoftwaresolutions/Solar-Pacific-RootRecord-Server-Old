package com.rootrecord.avaops.ops

import com.rootrecord.avaops.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpsApiClient(
    private val baseUrl: String = BuildConfig.OPS_API_BASE_URL,
    private val accessToken: String? = null,
    override val transport: OpsTransport = OpsTransport.LOCALHOST,
) : TransportAwareOpsProvider {
    override suspend fun servers(): List<OpsServer> = withContext(Dispatchers.IO) {
        val response = request("GET", "/api/ops/servers")
        val items = response.optJSONArray("servers") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    OpsServer(
                        id = item.getString("id"),
                        name = item.optString("name", item.getString("id")),
                        provider = ProviderKind.ROOTRECORD,
                        status = item.optString("status").toServerStatus(),
                        playersOnline = item.optIntOrNull("players_online"),
                        playerCapacity = item.optIntOrNull("player_capacity"),
                        address = item.optString("address").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    override suspend fun perform(serverId: String, action: ServerAction): OpsServer = withContext(Dispatchers.IO) {
        val body = JSONObject().put("server_id", serverId).put("action", action.name.lowercase())
        val response = request("POST", "/api/ops/perform", body)
        val server = response.getJSONObject("server")
        OpsServer(
            id = server.getString("id"),
            name = server.optString("name", server.getString("id")),
            provider = ProviderKind.ROOTRECORD,
            status = server.optString("status").toServerStatus(),
            playersOnline = server.optIntOrNull("players_online"),
            playerCapacity = server.optIntOrNull("player_capacity"),
            address = server.optString("address").takeIf { it.isNotBlank() },
        )
    }

    override suspend fun rcon(serverId: String, command: String): RconResult = withContext(Dispatchers.IO) {
        val response = request(
            "POST",
            "/api/ops/rcon",
            JSONObject().put("server_id", serverId).put("command", command),
        )
        RconResult(response.optBoolean("accepted"), response.optString("output"))
    }

    override suspend fun features(): FeatureFlagsSnapshot = withContext(Dispatchers.IO) {
        request("GET", "/api/ops/features").toFeatureFlagsSnapshot()
    }

    override suspend fun setFeatures(flags: Map<String, Boolean>): FeatureFlagsSnapshot =
        withContext(Dispatchers.IO) {
            val flagsJson = JSONObject()
            flags.forEach { (key, value) -> flagsJson.put(key, value) }
            request("POST", "/api/ops/features", JSONObject().put("flags", flagsJson)).toFeatureFlagsSnapshot()
        }

    override suspend fun idleStop(): Boolean = withContext(Dispatchers.IO) {
        request("POST", "/api/ops/idle-stop", JSONObject()).optBoolean("ok", false)
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            accessToken?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
        }
        connection.inputStream.bufferedReader().use { reader ->
            val payload = reader.readText()
            if (connection.responseCode !in 200..299) error("ops_http_${connection.responseCode}")
            return JSONObject(payload)
        }
    }
}

private fun String.toServerStatus(): ServerStatus = when (lowercase()) {
    "online", "running" -> ServerStatus.ONLINE
    "offline", "stopped" -> ServerStatus.OFFLINE
    "starting", "restarting" -> ServerStatus.STARTING
    else -> ServerStatus.UNKNOWN
}

private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

internal fun JSONObject.toFeatureFlagsSnapshot(): FeatureFlagsSnapshot {
    val flagsObj = optJSONObject("flags")
    val flags = mutableMapOf<String, Boolean>()
    flagsObj?.keys()?.forEach { key ->
        flags[key] = flagsObj.optBoolean(key)
    }
    return FeatureFlagsSnapshot(
        ok = optBoolean("ok", true),
        flags = flags,
        obsProcessRunning = optBoolean("obs_process_running"),
        obsLaunchesProcess = optBoolean("obs_launches_process"),
        detail = optString("detail").takeIf { it.isNotBlank() },
    )
}
