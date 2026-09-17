package com.rootrecord.rootmc.ui.chambers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.SpawnerMobType
import com.rootrecord.rootmc.data.local.entity.SpawnerTrackEntity
import com.rootrecord.rootmc.data.local.entity.SpawnerType
import com.rootrecord.rootmc.data.local.entity.TrialChamberEntity
import com.rootrecord.rootmc.data.repository.ChamberRepository
import com.rootrecord.rootmc.domain.model.SpawnerCooldown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpawnerRow(
    val spawner: SpawnerTrackEntity,
    val status: SpawnerCooldown.Status,
    val remainingMs: Long,
)

data class ChamberDetailUiState(
    val chamber: TrialChamberEntity? = null,
    val spawnerRows: List<SpawnerRow> = emptyList(),
    val nowMs: Long = System.currentTimeMillis(),
    val showAddSpawner: Boolean = false,
    val addLabel: String = "",
    val addType: SpawnerType = SpawnerType.TRIAL,
    val addMob: SpawnerMobType = SpawnerMobType.ZOMBIE,
    val addCustomMob: String = "",
    val addX: String = "",
    val addY: String = "",
    val addZ: String = "",
    val addDimension: MinecraftDimension = MinecraftDimension.OVERWORLD,
    val pendingDeleteSpawner: SpawnerTrackEntity? = null,
)

@HiltViewModel
class ChamberDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chamberRepository: ChamberRepository,
) : ViewModel() {

    private val chamberId: Long = checkNotNull(savedStateHandle.get<Long>("chamberId"))

    private val nowMs = MutableStateFlow(System.currentTimeMillis())
    private val _uiState = MutableStateFlow(ChamberDetailUiState())
    val uiState: StateFlow<ChamberDetailUiState> = _uiState.asStateFlow()

    private val chamberFlow = chamberRepository.observeChamber(chamberId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                chamberFlow.filterNotNull(),
                chamberRepository.observeSpawners(chamberId),
                nowMs,
            ) { chamber, spawners, tick ->
                Triple(chamber, spawners, tick)
            }.collect { (chamber, spawners, tick) ->
                val rows = spawners.map { spawner ->
                    SpawnerRow(
                        spawner = spawner,
                        status = SpawnerCooldown.statusFor(spawner, tick),
                        remainingMs = SpawnerCooldown.remainingMs(spawner, tick),
                    )
                }
                _uiState.update {
                    it.copy(
                        chamber = chamber,
                        spawnerRows = rows,
                        nowMs = tick,
                        addDimension = chamber.dimension.let { d ->
                            runCatching { MinecraftDimension.valueOf(d) }.getOrDefault(MinecraftDimension.OVERWORLD)
                        },
                    )
                }
            }
        }
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                nowMs.value = System.currentTimeMillis()
            }
        }
    }

    fun showAddSpawner() {
        val chamber = _uiState.value.chamber
        _uiState.update {
            it.copy(
                showAddSpawner = true,
                addX = chamber?.x?.toString().orEmpty(),
                addY = chamber?.y?.toString().orEmpty(),
                addZ = chamber?.z?.toString().orEmpty(),
            )
        }
    }

    fun dismissAddSpawner() = _uiState.update { it.copy(showAddSpawner = false) }
    fun updateAddLabel(v: String) = _uiState.update { it.copy(addLabel = v) }
    fun updateAddType(v: SpawnerType) = _uiState.update { it.copy(addType = v) }
    fun updateAddMob(v: SpawnerMobType) = _uiState.update { it.copy(addMob = v) }
    fun updateAddCustomMob(v: String) = _uiState.update { it.copy(addCustomMob = v) }
    fun updateAddX(v: String) = _uiState.update { it.copy(addX = v) }
    fun updateAddY(v: String) = _uiState.update { it.copy(addY = v) }
    fun updateAddZ(v: String) = _uiState.update { it.copy(addZ = v) }

    fun saveSpawner() {
        val state = _uiState.value
        val x = state.addX.toIntOrNull() ?: return
        val y = state.addY.toIntOrNull() ?: return
        val z = state.addZ.toIntOrNull() ?: return
        viewModelScope.launch {
            chamberRepository.addSpawner(
                chamberId = chamberId,
                label = state.addLabel,
                spawnerType = state.addType,
                mobType = if (state.addMob == SpawnerMobType.CUSTOM) {
                    SpawnerMobType.CUSTOM.name
                } else {
                    state.addMob.name
                },
                customMobLabel = if (state.addMob == SpawnerMobType.CUSTOM) state.addCustomMob else null,
                x = x,
                y = y,
                z = z,
                dimension = state.addDimension.name,
            )
            _uiState.update {
                it.copy(
                    showAddSpawner = false,
                    addLabel = "",
                    addCustomMob = "",
                )
            }
        }
    }

    fun markCleared(spawnerId: Long, ominousSkip: Boolean = false) {
        viewModelScope.launch {
            chamberRepository.markSpawnerCleared(spawnerId, ominousSkip)
        }
    }

    fun markReady(spawnerId: Long) {
        viewModelScope.launch {
            chamberRepository.markSpawnerReady(spawnerId)
        }
    }

    fun requestDeleteSpawner(spawner: SpawnerTrackEntity) {
        _uiState.update { it.copy(pendingDeleteSpawner = spawner) }
    }

    fun dismissDeleteSpawner() = _uiState.update { it.copy(pendingDeleteSpawner = null) }

    fun confirmDeleteSpawner() {
        val spawner = _uiState.value.pendingDeleteSpawner ?: return
        viewModelScope.launch {
            chamberRepository.deleteSpawner(spawner)
            _uiState.update { it.copy(pendingDeleteSpawner = null) }
        }
    }
}
