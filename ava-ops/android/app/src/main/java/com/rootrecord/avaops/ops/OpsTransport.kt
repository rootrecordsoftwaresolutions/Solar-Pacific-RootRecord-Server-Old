package com.rootrecord.avaops.ops

enum class OpsTransport {
    BLUETOOTH,
    LOCALHOST,
    CLOUDFLARE,
    DISCONNECTED,
}

interface TransportAwareOpsProvider : OpsProvider {
    val transport: OpsTransport
}