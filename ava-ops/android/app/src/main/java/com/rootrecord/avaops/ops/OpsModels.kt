package com.rootrecord.avaops.ops

enum class ProviderKind { ROOTRECORD, SHOCKBYTE, MOCK }

enum class ServerStatus { ONLINE, OFFLINE, STARTING, UNKNOWN }

enum class ServerAction { START, STOP, RESTART }

data class OpsServer(
    val id: String,
    val name: String,
    val provider: ProviderKind,
    val status: ServerStatus,
    val playersOnline: Int? = null,
    val playerCapacity: Int? = null,
    val address: String? = null,
)

data class AuditEvent(
    val message: String,
    val createdAt: Long,
)

data class RconResult(
    val accepted: Boolean,
    val output: String,
)

data class FeatureFlagsSnapshot(
    val ok: Boolean,
    val flags: Map<String, Boolean>,
    val obsProcessRunning: Boolean,
    val obsLaunchesProcess: Boolean,
    val detail: String? = null,
)

interface OpsProvider {
    suspend fun servers(): List<OpsServer>
    suspend fun perform(serverId: String, action: ServerAction): OpsServer
    suspend fun rcon(serverId: String, command: String): RconResult
    suspend fun features(): FeatureFlagsSnapshot
    suspend fun setFeatures(flags: Map<String, Boolean>): FeatureFlagsSnapshot
    suspend fun idleStop(): Boolean
}
