package com.rootrecord.rootmc.ui.chambers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.local.entity.WorldPlayMode
import com.rootrecord.rootmc.data.repository.ChamberRepository
import com.rootrecord.rootmc.data.repository.RealmGroup
import com.rootrecord.rootmc.data.repository.RealmRepository
import com.rootrecord.rootmc.data.repository.WorldRepository
import com.rootrecord.rootmc.domain.model.SpawnerCooldown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChamberFilter { ALL, READY, COOLING }

data class ChamberCard(
    val chamber: TrialChamberEntity,
    val spawners: List<SpawnerTrackEntity>,
    val readyCount: Int,
    val coolingCount: Int,
    val farmingCount: Int,
    val nextReadyMs: Long?,
)

data class ChambersUiState(
    val activeWorld: WorldEntity? = null,
    val allCards: List<ChamberCard> = emptyList(),
    val filteredCards: List<ChamberCard> = emptyList(),
    val filter: ChamberFilter = ChamberFilter.ALL,
    val nowMs: Long = System.currentTimeMillis(),
    val realmGroups: List<RealmGroup> = emptyList(),
    val showAddDialog: Boolean = false,
    val addLabel: String = "",
    val addNotes: String = "",
    val addX: String = "",
    val addY: String = "",
    val addZ: String = "",
    val addDimension: MinecraftDimension = MinecraftDimension.OVERWORLD,
    val addGroupId: String? = null,
    val syncMessage: String? = null,
)

@HiltViewModel
class ChambersViewModel @Inject constructor(
    worldRepository: WorldRepository,
    private val chamberRepository: ChamberRepository,
    private val realmRepository: RealmRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChambersUiState())
    val uiState: StateFlow<ChambersUiState> = _uiState.asStateFlow()

    private val nowMs = MutableStateFlow(System.currentTimeMillis())
    private var tickJob: Job? = null

    private val syncedGroupIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            worldRepository.observeActiveWorld()
                .flatMapLatest { world ->
                    if (world == null) {
                        flowOf(Triple<WorldEntity?, List<TrialChamberEntity>, List<SpawnerTrackEntity>>(
                            null, emptyList(), emptyList(),
                        ))
                    } else {
                        combine(
                            chamberRepository.observeChambersByWorld(world.id),
                            chamberRepository.observeSpawnersByWorld(world.id),
                        ) { chambers, spawners ->
                            Triple(world, chambers, spawners)
                        }
                    }
                }
                .combine(nowMs) { triple, tick ->
                    triple to tick
                }
                .collect { (triple, tick) ->
                    val (world, chambers, spawners) = triple
                    world?.let { syncLinkedGroups(it, chambers) }
                    val byChamber = spawners.groupBy { it.chamberId }
                    val cards = chambers.map { chamber ->
                        toCard(chamber, byChamber[chamber.id].orEmpty(), tick)
                    }.sortedWith(
                        compareBy<ChamberCard> { card ->
                            when {
                                card.readyCount > 0 -> 0
                                card.coolingCount > 0 -> 1
                                else -> 2
                            }
                        }.thenBy { it.nextReadyMs ?: Long.MAX_VALUE }
                            .thenBy { it.chamber.label.lowercase() },
                    )
                    _uiState.update { state ->
                        state.copy(
                            activeWorld = world,
                            allCards = cards,
                            filteredCards = filterCards(cards, state.filter),
                            nowMs = tick,
                        )
                    }
                }
        }
        startTicker()
        loadGroups()
    }

    fun setFilter(filter: ChamberFilter) {
        _uiState.update { state ->
            state.copy(filter = filter, filteredCards = filterCards(state.allCards, filter))
        }
    }

    fun showAddDialog() = _uiState.update { it.copy(showAddDialog = true) }
    fun dismissAddDialog() = _uiState.update { it.copy(showAddDialog = false, syncMessage = null) }

    fun updateAddLabel(v: String) = _uiState.update { it.copy(addLabel = v) }
    fun updateAddNotes(v: String) = _uiState.update { it.copy(addNotes = v) }
    fun updateAddX(v: String) = _uiState.update { it.copy(addX = v) }
    fun updateAddY(v: String) = _uiState.update { it.copy(addY = v) }
    fun updateAddZ(v: String) = _uiState.update { it.copy(addZ = v) }
    fun updateAddDimension(v: MinecraftDimension) = _uiState.update { it.copy(addDimension = v) }
    fun updateAddGroupId(v: String?) = _uiState.update { it.copy(addGroupId = v) }

    fun saveChamber() {
        val state = _uiState.value
        val world = state.activeWorld ?: return
        viewModelScope.launch {
            chamberRepository.createChamber(
                world = world,
                label = state.addLabel,
                notes = state.addNotes,
                x = state.addX.toIntOrNull(),
                y = state.addY.toIntOrNull(),
                z = state.addZ.toIntOrNull(),
                dimension = state.addDimension.name,
                realmGroupId = if (world.playMode == WorldPlayMode.MULTIPLAYER.name) state.addGroupId else null,
            )
            _uiState.update {
                it.copy(
                    showAddDialog = false,
                    addLabel = "",
                    addNotes = "",
                    addX = "",
                    addY = "",
                    addZ = "",
                    addGroupId = null,
                )
            }
        }
    }

    fun refreshGroupSync() {
        val world = _uiState.value.activeWorld ?: return
        viewModelScope.launch {
            val groupIds = _uiState.value.allCards.mapNotNull { it.chamber.realmGroupId }.distinct()
            if (groupIds.isEmpty()) {
                _uiState.update { it.copy(syncMessage = "No group-linked chambers on this world.") }
                return@launch
            }
            var ok = 0
            groupIds.forEach { groupId ->
                chamberRepository.syncGroupChambers(groupId, world).onSuccess { ok++ }
            }
            _uiState.update {
                it.copy(syncMessage = "Synced $ok group chamber set(s).")
            }
        }
    }

    private fun loadGroups() {
        viewModelScope.launch {
            realmRepository.listGroups()
                .onSuccess { (groups, _) ->
                    _uiState.update { it.copy(realmGroups = groups) }
                }
        }
    }

    private fun syncLinkedGroups(world: WorldEntity, chambers: List<TrialChamberEntity>) {
        if (world.playMode != WorldPlayMode.MULTIPLAYER.name) return
        val groupIds = chambers.mapNotNull { it.realmGroupId }.distinct()
            .filter { it !in syncedGroupIds }
        if (groupIds.isEmpty()) return
        viewModelScope.launch {
            groupIds.forEach { groupId ->
                chamberRepository.syncGroupChambers(groupId, world)
                syncedGroupIds.add(groupId)
            }
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                nowMs.value = System.currentTimeMillis()
            }
        }
    }

    private fun toCard(chamber: TrialChamberEntity, spawners: List<SpawnerTrackEntity>, nowMs: Long): ChamberCard {
        var ready = 0
        var cooling = 0
        var farming = 0
        var nextReady: Long? = null
        for (spawner in spawners) {
            when (SpawnerCooldown.statusFor(spawner, nowMs)) {
                SpawnerCooldown.Status.READY -> ready++
                SpawnerCooldown.Status.COOLING -> {
                    cooling++
                    val remaining = SpawnerCooldown.remainingMs(spawner, nowMs)
                    val endsAt = nowMs + remaining
                    if (nextReady == null || endsAt < nextReady) nextReady = endsAt
                }
                SpawnerCooldown.Status.FARMING -> farming++
            }
        }
        return ChamberCard(chamber, spawners, ready, cooling, farming, nextReady)
    }

    private fun filterCards(cards: List<ChamberCard>, filter: ChamberFilter): List<ChamberCard> =
        when (filter) {
            ChamberFilter.ALL -> cards
            ChamberFilter.READY -> cards.filter { it.readyCount > 0 || it.spawners.isEmpty() }
            ChamberFilter.COOLING -> cards.filter { it.coolingCount > 0 }
        }
}
