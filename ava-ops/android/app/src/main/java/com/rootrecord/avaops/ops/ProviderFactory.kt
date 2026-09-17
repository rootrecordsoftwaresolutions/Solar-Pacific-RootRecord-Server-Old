package com.rootrecord.avaops.ops

import android.content.Context

fun createOpsProvider(context: Context, config: OpsConfig? = null): TransportAwareOpsProvider {
    val cfg = config ?: OpsConfigStore(context).load()
    val bluetooth = BluetoothOpsProvider(
        context = context,
        deviceAddress = cfg.bluetoothDeviceAddress,
        deviceName = cfg.bluetoothDeviceName,
        token = cfg.authToken.takeIf { it.isNotBlank() },
    )
    val providers = mutableListOf<TransportAwareOpsProvider>(bluetooth)
    if (cfg.localApiBaseUrl.isConfiguredHttpBase()) {
        providers += OpsApiClient(
            baseUrl = cfg.localApiBaseUrl,
            accessToken = cfg.authToken.takeIf { it.isNotBlank() },
            transport = OpsTransport.LOCALHOST,
        )
    }
    if (cfg.cloudflareApiBaseUrl.isConfiguredHttpBase()) {
        providers += OpsApiClient(
            baseUrl = cfg.cloudflareApiBaseUrl,
            accessToken = cfg.authToken.takeIf { it.isNotBlank() },
            transport = OpsTransport.CLOUDFLARE,
        )
    }
    return FallbackOpsProvider(providers)
}

private fun String.isConfiguredHttpBase(): Boolean {
    val value = trim()
    if (value.isEmpty()) return false
    // Stale README host — not a RootRecord public surface; reject prefs/BuildConfig too.
    if (value.contains("ops.rootrecord.online", ignoreCase = true)) return false
    return true
}