package com.rootrecord.rootmc.ui.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.FeaturedServerConfig
import com.rootrecord.rootmc.data.repository.McMMOStats
import com.rootrecord.rootmc.data.repository.NetWorthLeaderboardEntry
import com.rootrecord.rootmc.data.repository.NetWorthStats
import com.rootrecord.rootmc.data.repository.PlaytimeLeaderboardEntry
import com.rootrecord.rootmc.data.repository.PlaytimeStats
import com.rootrecord.rootmc.data.repository.RootShopsSnapshot
import com.rootrecord.rootmc.data.repository.ServerItemTotal
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.domain.usecase.SyncDedicatedServerUseCase
import com.rootrecord.rootmc.domain.usecase.SyncIngameEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootMcServerUiState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val server: FeaturedServerConfig? = null,
    val minecraftLinked: Boolean = false,
    val minecraftUsername: String? = null,
    val mcmmo: McMMOStats? = null,
    val playtime: PlaytimeStats? = null,
    val netWorth: NetWorthStats? = null,
    val netWorthLeaderboard: List<NetWorthLeaderboardEntry> = emptyList(),
    val playtimeLeaderboard: List<PlaytimeLeaderboardEntry> = emptyList(),
    val serverItemTotals: List<ServerItemTotal> = emptyList(),
    val rootShops: RootShopsSnapshot? = null,
    val isMayor: Boolean = false,
    val marketStacks: Int? = null,
    val reserveBalance: Double? = null,
    val worldAddedMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class RootMcServerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prefs: RootMcPreferences,
    private val serverRepository: ServerRepository,
    private val syncDedicatedServer: SyncDedicatedServerUseCase,
    private val syncIngameEvents: SyncIngameEventsUseCase,
) : ViewModel() {

    /** Optional deep-link server id; RootMC tab uses the configured dedicated server. */
    private val navServerId: String? = savedStateHandle.get<String>("serverId")

    private val _uiState = MutableStateFlow(RootMcServerUiState())
    val uiState: StateFlow<RootMcServerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, worldAddedMessage = null) }
            val signedIn = prefs.authSignedIn.first()

            val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
            val serverId = navServerId ?: config?.serverId
            if (serverId == null) {
                _uiState.update {
                    it.copy(loading = false, error = "Could not load RootMC server info.")
                }
                return@launch
            }

            var server: FeaturedServerConfig? = config?.takeIf { it.serverId == serverId }
            var minecraftLinked = false
            var minecraftUsername: String? = null
            var mcmmo: McMMOStats? = server?.mcmmo
            var playtime: PlaytimeStats? = server?.playtime
            var netWorth: NetWorthStats? = server?.netWorth
            var worldMessage: String? = null

            if (signedIn) {
                val membership = serverRepository.fetchMembership().getOrNull()
                minecraftLinked = membership?.minecraftLinked == true
                minecraftUsername = membership?.minecraftUsername
                server = membership?.servers?.find { it.serverId == serverId }
                    ?: membership?.featuredServer?.takeIf { it.serverId == serverId }
                    ?: server
                mcmmo = server?.mcmmo ?: mcmmo
                playtime = server?.playtime ?: playtime
                netWorth = server?.netWorth ?: netWorth
                if (minecraftLinked && mcmmo == null) {
                    mcmmo = serverRepository.fetchMyMcmmoOnServer(serverId).getOrNull()
                }
                if (minecraftLinked && playtime == null) {
                    playtime = serverRepository.fetchMyPlaytimeOnServer(serverId).getOrNull()
                }
                if (minecraftLinked && netWorth == null) {
                    netWorth = serverRepository.fetchMyNetWorthOnServer(serverId).getOrNull()
                }
                syncDedicatedServer.sync().onSuccess { result ->
                    if (result.created) {
                        worldMessage = "Added ${server?.defaultWorldName ?: "RootMC"} to your worlds."
                    }
                }
                syncIngameEvents.sync()
            }

            if (server == null) {
                server = config
            }

            val leaderboard = serverRepository.fetchNetWorthLeaderboard(serverId).getOrDefault(emptyList())
            val playtimeLeaderboard = serverRepository.fetchPlaytimeLeaderboard(serverId).getOrDefault(emptyList())
            val itemTotals = serverRepository.fetchServerItemTotals(serverId).getOrDefault(emptyList())
            val rootShops = serverRepository.fetchRootShops(serverId).getOrNull()
            var isMayor = false
            var marketStacks: Int? = null
            var reserveBalance: Double? = null
            if (signedIn && minecraftLinked) {
                isMayor = serverRepository.fetchMayorTown(serverId).getOrNull()?.isMayor == true
            }
            if (signedIn) {
                marketStacks = serverRepository.fetchStockMarketSummary(serverId).getOrNull()?.totalServerItems
                reserveBalance = serverRepository.fetchTreasuryReserve().getOrNull()?.balance
            }

            _uiState.update {
                it.copy(
                    loading = false,
                    signedIn = signedIn,
                    server = server,
                    minecraftLinked = minecraftLinked,
                    minecraftUsername = minecraftUsername,
                    mcmmo = mcmmo,
                    playtime = playtime,
                    netWorth = netWorth,
                    netWorthLeaderboard = leaderboard,
                    playtimeLeaderboard = playtimeLeaderboard,
                    serverItemTotals = itemTotals,
                    rootShops = rootShops,
                    isMayor = isMayor,
                    marketStacks = marketStacks,
                    reserveBalance = reserveBalance,
                    worldAddedMessage = worldMessage,
                    error = if (server == null) "Could not load RootMC server info." else null,
                )
            }
        }
    }
}
