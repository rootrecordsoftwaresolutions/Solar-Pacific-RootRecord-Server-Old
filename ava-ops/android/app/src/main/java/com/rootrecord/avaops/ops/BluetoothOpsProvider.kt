package com.rootrecord.avaops.ops

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class BluetoothOpsProvider(
    context: Context,
    private val deviceAddress: String = "",
    private val deviceName: String = "",
    private val token: String? = null,
    private val serviceUuid: UUID = OPS_RFCOMM_UUID,
) : TransportAwareOpsProvider {
    private val adapter = context.getSystemService(BluetoothAdapter::class.java)

    override val transport = OpsTransport.BLUETOOTH

    override suspend fun servers(): List<OpsServer> = withContext(Dispatchers.IO) {
        val req = JSONObject().put("op", "servers")
        token?.let { req.put("token", it) }
        val response = request(req)
        val items = response.optJSONArray("servers") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) add(items.getJSONObject(index).toOpsServer())
        }
    }

    override suspend fun perform(serverId: String, action: ServerAction): OpsServer =
        withContext(Dispatchers.IO) {
            val req = JSONObject()
                .put("op", "perform")
                .put("server_id", serverId)
                .put("action", action.name.lowercase())
            token?.let { req.put("token", it) }
            val response = request(req)
            val serverObj = response.optJSONObject("server") ?: error("missing_server_in_response")
            serverObj.toOpsServer()
        }

    override suspend fun rcon(serverId: String, command: String): RconResult =
        withContext(Dispatchers.IO) {
            val req = JSONObject()
                .put("op", "rcon")
                .put("server_id", serverId)
                .put("command", command)
            token?.let { req.put("token", it) }
            val response = request(req)
            RconResult(
                accepted = response.optBoolean("accepted", response.optBoolean("ok", false)),
                output = response.optString("output", response.optString("detail", "")),
            )
        }

    override suspend fun features(): FeatureFlagsSnapshot = withContext(Dispatchers.IO) {
        val req = JSONObject().put("op", "features")
        token?.let { req.put("token", it) }
        request(req).toFeatureFlagsSnapshot()
    }

    override suspend fun setFeatures(flags: Map<String, Boolean>): FeatureFlagsSnapshot =
        withContext(Dispatchers.IO) {
            val flagsJson = JSONObject()
            flags.forEach { (key, value) -> flagsJson.put(key, value) }
            val req = JSONObject()
                .put("op", "set_features")
                .put("flags", flagsJson)
            token?.let { req.put("token", it) }
            request(req).toFeatureFlagsSnapshot()
        }

    override suspend fun idleStop(): Boolean = withContext(Dispatchers.IO) {
        val req = JSONObject().put("op", "idle_stop")
        token?.let { req.put("token", it) }
        request(req).optBoolean("ok", false)
    }

    suspend fun dashboard(): AvaBoardSnapshot = withContext(Dispatchers.IO) {
        val req = JSONObject().put("op", "dashboard")
        token?.let { req.put("token", it) }
        val response = request(req, requireOk = false)
        val looksLikeBoard = response.has("generated_at") || response.has("weather") || response.has("host")
        if (!looksLikeBoard && response.optBoolean("ok", true).not()) {
            error(response.optString("detail", "dashboard unavailable"))
        }
        response.toBoardSnapshot()
    }

    @SuppressLint("MissingPermission")
    private fun request(payload: JSONObject, requireOk: Boolean = true): JSONObject {
        synchronized(requestLock) {
            val bluetooth = adapter ?: error("bluetooth_unavailable")
            if (!bluetooth.isEnabled) error("bluetooth_disabled")
            val device = resolveDevice(bluetooth)
            bluetooth.cancelDiscovery()
            val socket = openSocket(device)
            try {
                val out = socket.outputStream
                out.write((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                val line = readJsonLine(socket.inputStream)
                val response = JSONObject(line)
                if (requireOk && response.optBoolean("ok", true).not()) {
                    error(response.optString("detail", "bluetooth_request_failed"))
                }
                return response
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun readJsonLine(input: InputStream): String {
        val collected = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            var cut = -1
            for (i in 0 until n) {
                if (buf[i] == '\n'.code.toByte()) {
                    cut = i
                    break
                }
            }
            if (cut >= 0) {
                collected.write(buf, 0, cut)
                break
            }
            collected.write(buf, 0, n)
            if (collected.size() > MAX_RFCOMM_JSON_BYTES) error("bluetooth_response_too_large")
        }
        val line = collected.toString(Charsets.UTF_8.name())
        if (line.isBlank()) error("bluetooth_empty_response")
        return line
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        val makers = listOf<(BluetoothDevice) -> BluetoothSocket>(
            { it.createInsecureRfcommSocketToServiceRecord(serviceUuid) },
            { it.createRfcommSocketToServiceRecord(serviceUuid) },
            { dev ->
                val method = dev.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(dev, RFCOMM_CHANNEL) as BluetoothSocket
            },
        )
        var lastError: Throwable? = null
        for (make in makers) {
            val socket = try {
                make(device)
            } catch (error: Throwable) {
                lastError = error
                continue
            }
            try {
                socket.connect()
                return socket
            } catch (error: Throwable) {
                lastError = error
                runCatching { socket.close() }
            }
        }
        throw lastError ?: IllegalStateException("bluetooth_connect_failed")
    }

    @SuppressLint("MissingPermission")
    private fun resolveDevice(bluetooth: BluetoothAdapter): BluetoothDevice {
        val address = deviceAddress.trim()
        if (address.isNotEmpty()) {
            return runCatching { bluetooth.getRemoteDevice(address) }.getOrNull()
                ?: error("invalid_bluetooth_address: $address")
        }

        val bonded = runCatching { bluetooth.bondedDevices }.getOrNull()?.toList() ?: emptyList()
        val name = deviceName.trim()
        if (name.isNotEmpty()) {
            bonded.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
            bonded.firstOrNull { it.name?.contains(name, ignoreCase = true) == true }?.let { return it }
        }

        bonded.firstOrNull { dev ->
            val devName = dev.name?.lowercase() ?: ""
            devName.contains("ava") || devName.contains("rootrecord") || devName.contains("desk")
        }?.let { return it }

        if (bonded.size == 1) {
            return bonded.first()
        }

        error("bluetooth_device_not_configured")
    }

    companion object {
        val OPS_RFCOMM_UUID: UUID = UUID.fromString("6f70735f-7265-6c61-7963-6f6e6e656374")
        const val RFCOMM_CHANNEL: Int = 6
        private const val MAX_RFCOMM_JSON_BYTES: Int = 512 * 1024
        private val requestLock = Any()
    }
}

private fun JSONObject.toOpsServer(): OpsServer = OpsServer(
    id = getString("id"),
    name = optString("name", getString("id")),
    provider = ProviderKind.ROOTRECORD,
    status = optString("status").toServerStatusForBluetooth(),
    playersOnline = optIntOrNullForBluetooth("players_online"),
    playerCapacity = optIntOrNullForBluetooth("player_capacity"),
    address = optString("address").takeIf { it.isNotBlank() },
)

private fun String.toServerStatusForBluetooth(): ServerStatus = when (lowercase()) {
    "online", "running" -> ServerStatus.ONLINE
    "offline", "stopped" -> ServerStatus.OFFLINE
    "starting", "restarting" -> ServerStatus.STARTING
    else -> ServerStatus.UNKNOWN
}

private fun JSONObject.optIntOrNullForBluetooth(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null