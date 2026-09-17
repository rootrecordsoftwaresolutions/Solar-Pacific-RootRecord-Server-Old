package com.rootrecord.avaops.ops

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import com.rootrecord.avaops.BuildConfig

data class OpsConfig(
    val bluetoothDeviceAddress: String = "",
    val bluetoothDeviceName: String = "",
    val localApiBaseUrl: String = "",
    val cloudflareApiBaseUrl: String = "",
    val authToken: String = "",
)

class OpsConfigStore(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ava_ops_config", Context.MODE_PRIVATE)

    fun load(): OpsConfig {
        return OpsConfig(
            bluetoothDeviceAddress = prefs.getString(KEY_BT_ADDRESS, BuildConfig.OPS_BLUETOOTH_DEVICE_ADDRESS) ?: "",
            bluetoothDeviceName = prefs.getString(KEY_BT_NAME, BuildConfig.OPS_BLUETOOTH_DEVICE_NAME) ?: "",
            localApiBaseUrl = prefs.getString(KEY_LOCAL_URL, null)
                ?: BuildConfig.OPS_LOCAL_API_BASE_URL,
            cloudflareApiBaseUrl = prefs.getString(KEY_CF_URL, null)
                ?: BuildConfig.OPS_CLOUDFLARE_API_BASE_URL,
            authToken = prefs.getString(KEY_TOKEN, "") ?: "",
        )
    }

    fun save(config: OpsConfig) {
        prefs.edit()
            .putString(KEY_BT_ADDRESS, config.bluetoothDeviceAddress.trim())
            .putString(KEY_BT_NAME, config.bluetoothDeviceName.trim())
            .putString(KEY_LOCAL_URL, config.localApiBaseUrl.trim())
            .putString(KEY_CF_URL, config.cloudflareApiBaseUrl.trim())
            .putString(KEY_TOKEN, config.authToken.trim())
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(): List<Pair<String, String>> {
        val adapter = context.getSystemService(BluetoothAdapter::class.java) ?: return emptyList()
        return try {
            val devices = adapter.bondedDevices ?: return emptyList()
            devices.map { device ->
                val name = runCatching { device.name }.getOrNull() ?: "Unknown Device"
                val address = runCatching { device.address }.getOrNull() ?: ""
                name to address
            }.filter { it.second.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_BT_ADDRESS = "bt_address"
        private const val KEY_BT_NAME = "bt_name"
        private const val KEY_LOCAL_URL = "local_url"
        private const val KEY_CF_URL = "cf_url"
        private const val KEY_TOKEN = "auth_token"
    }
}
