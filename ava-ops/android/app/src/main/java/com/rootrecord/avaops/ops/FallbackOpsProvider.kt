package com.rootrecord.avaops.ops

class FallbackOpsProvider(
    private val providers: List<TransportAwareOpsProvider>,
) : TransportAwareOpsProvider {
    init {
        require(providers.isNotEmpty()) { "at_least_one_ops_provider_required" }
    }

    override var transport: OpsTransport = providers.first().transport
        private set

    override suspend fun servers(): List<OpsServer> = execute { it.servers() }

    override suspend fun perform(serverId: String, action: ServerAction): OpsServer =
        execute { it.perform(serverId, action) }

    override suspend fun rcon(serverId: String, command: String): RconResult =
        execute { it.rcon(serverId, command) }

    override suspend fun features(): FeatureFlagsSnapshot = execute { it.features() }

    override suspend fun setFeatures(flags: Map<String, Boolean>): FeatureFlagsSnapshot =
        execute { it.setFeatures(flags) }

    override suspend fun idleStop(): Boolean = execute { it.idleStop() }

    private suspend fun <T> execute(call: suspend (TransportAwareOpsProvider) -> T): T {
        var lastError: Throwable? = null
        for (provider in providers) {
            try {
                val result = call(provider)
                transport = provider.transport
                return result
            } catch (error: Throwable) {
                lastError = error
            }
        }
        transport = OpsTransport.DISCONNECTED
        throw lastError ?: IllegalStateException("no_ops_provider_available")
    }
}