package com.rootrecord.avaops.ops

import kotlinx.coroutines.delay

class MockOpsProvider : OpsProvider {
    private val initialServers = listOf(
        OpsServer("prod", "Production", ProviderKind.ROOTRECORD, ServerStatus.ONLINE, 3, 20, "play.rootmc.net"),
        OpsServer("test", "Test", ProviderKind.ROOTRECORD, ServerStatus.OFFLINE, 0, 10, "test.rootmc.net"),
    )

    private val state = initialServers.toMutableList()

    override suspend fun servers(): List<OpsServer> = state.toList()

    override suspend fun perform(serverId: String, action: ServerAction): OpsServer {
        val index = state.indexOfFirst { it.id == serverId }
        require(index >= 0) { "unknown_server" }
        val current = state[index]
        val next = when (action) {
            ServerAction.START, ServerAction.RESTART -> current.copy(
                status = ServerStatus.STARTING,
                playersOnline = null,
            )
            ServerAction.STOP -> current.copy(status = ServerStatus.OFFLINE, playersOnline = 0)
        }
        state[index] = next
        delay(250)
        return next
    }

    override suspend fun rcon(serverId: String, command: String): RconResult {
        require(state.any { it.id == serverId }) { "unknown_server" }
        val trimmed = command.trim()
        require(trimmed in ALLOWED_COMMANDS || trimmed.startsWith("say ")) { "command_not_allowlisted" }
        return RconResult(true, "mock accepted: $trimmed")
    }

    override suspend fun features(): FeatureFlagsSnapshot = FeatureFlagsSnapshot(
        ok = true,
        flags = mapOf(
            "obs" to false,
            "radio_on_air" to false,
            "music_bed" to false,
            "morning_report" to false,
            "midday_report" to false,
            "startup_voice" to false,
            "cloud_report_spend" to false,
            "companions_poller" to false,
            "local_edge" to false,
        ),
        obsProcessRunning = false,
        obsLaunchesProcess = false,
    )

    override suspend fun setFeatures(flags: Map<String, Boolean>): FeatureFlagsSnapshot =
        features().copy(flags = features().flags + flags)

    override suspend fun idleStop(): Boolean = true

    companion object {
        val ALLOWED_COMMANDS = setOf("list", "save-all", "weather clear", "weather rain")
    }
}
