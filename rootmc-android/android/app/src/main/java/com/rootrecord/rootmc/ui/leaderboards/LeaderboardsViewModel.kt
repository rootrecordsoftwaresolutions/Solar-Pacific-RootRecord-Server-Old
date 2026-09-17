package com.rootrecord.rootmc.ui.leaderboards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.GoldFoundLeaderboardEntry
import com.rootrecord.rootmc.data.repository.GoldMintLeaderboardEntry
import com.rootrecord.rootmc.data.repository.McmmoLeaderboardEntry
import com.rootrecord.rootmc.data.repository.NetWorthLeaderboardEntry
import com.rootrecord.rootmc.data.repository.PlaytimeLeaderboardEntry
import com.rootrecord.rootmc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardsUiState(
    val loading: Boolean = true,
    val serverName: String = "RootMC",
    val netWorth: List<NetWorthLeaderboardEntry> = emptyList(),
    val playtime: List<PlaytimeLeaderboardEntry> = emptyList(),
    val mcmmo: List<McmmoLeaderboardEntry> = emptyList(),
    val goldMinted: List<GoldMintLeaderboardEntry> = emptyList(),
    val goldFound: List<GoldFoundLeaderboardEntry> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LeaderboardsViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardsUiState())
    val uiState: StateFlow<LeaderboardsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
                val serverId = config?.serverId ?: "rootmc"
                val serverName = config?.name ?: "RootMC"
                coroutineScope {
                    val netWorth = async { serverRepository.fetchNetWorthLeaderboard(serverId, 25) }
                    val playtime = async { serverRepository.fetchPlaytimeLeaderboard(serverId, 25) }
                    val mcmmo = async { serverRepository.fetchMcmmoLeaderboard(serverId, 25) }
                    val goldMinted = async { serverRepository.fetchGoldMintLeaderboard("rootmc", 25) }
                    val goldFound = async { serverRepository.fetchGoldFoundLeaderboard(serverId, 25) }
                    LeaderboardsUiState(
                        loading = false,
                        serverName = serverName,
                        netWorth = netWorth.await().getOrDefault(emptyList()),
                        playtime = playtime.await().getOrDefault(emptyList()),
                        mcmmo = mcmmo.await().getOrDefault(emptyList()),
                        goldMinted = goldMinted.await().getOrDefault(emptyList()),
                        goldFound = goldFound.await().getOrDefault(emptyList()),
                    )
                }
            }.onSuccess { loaded ->
                _uiState.value = loaded
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = err.message ?: "Could not load leaderboards.",
                    )
                }
            }
        }
    }
}
