package com.rootrecord.rootmc.ui.coords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.CoordinateEntity
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.local.dao.IngameImportDao
import com.rootrecord.rootmc.data.repository.CoordinateRepository
import com.rootrecord.rootmc.data.repository.TimelineRepository
import com.rootrecord.rootmc.data.repository.WorldRepository
import com.rootrecord.rootmc.domain.model.CoordinateFormats
import com.rootrecord.rootmc.domain.usecase.DistanceBetweenCoordsUseCase
import com.rootrecord.rootmc.domain.usecase.NetherPortalCalcUseCase
import com.rootrecord.rootmc.domain.usecase.SaveCoordinateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoordPairSummary(
    val distanceText: String,
    val bearingText: String?,
    val alignmentText: String?,
)

data class CoordsUiState(
    val activeWorld: WorldEntity? = null,
    val coordinates: List<CoordinateEntity> = emptyList(),
    val ingameWaypointIds: Set<Long> = emptySet(),
    val selectedA: Long? = null,
    val selectedB: Long? = null,
    val pairSummary: CoordPairSummary? = null,
    val portalText: String? = null,
    val portalOverworldX: String = "",
    val portalOverworldZ: String = "",
    val showAddDialog: Boolean = false,
    val editingCoordId: Long? = null,
    val addLabel: String = "",
    val addX: String = "",
    val addY: String = "64",
    val addZ: String = "",
    val addDimension: MinecraftDimension = MinecraftDimension.OVERWORLD,
)

@HiltViewModel
class CoordsViewModel @Inject constructor(
    worldRepository: WorldRepository,
    private val coordinateRepository: CoordinateRepository,
    private val saveCoordinateUseCase: SaveCoordinateUseCase,
    private val distanceUseCase: DistanceBetweenCoordsUseCase,
    private val portalCalcUseCase: NetherPortalCalcUseCase,
    private val timelineRepository: TimelineRepository,
    private val ingameImportDao: IngameImportDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoordsUiState())
    val uiState: StateFlow<CoordsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            worldRepository.observeActiveWorld().collect { world ->
                _uiState.update { it.copy(activeWorld = world) }
                world?.let {
                    runCatching { coordinateRepository.backfillChunkAreas(it.id) }
                        .onFailure { /* areas table may still be migrating */ }
                }
            }
        }
        viewModelScope.launch {
            worldRepository.observeActiveWorld()
                .flatMapLatest { world ->
                    if (world == null) flowOf(emptyList())
                    else coordinateRepository.observeByWorld(world.id)
                }
                .collect { coords ->
                    val ingameIds = runCatching { ingameImportDao.allWaypointIds().toSet() }
                        .getOrDefault(emptySet())
                    _uiState.update { state ->
                        val updated = state.copy(coordinates = coords, ingameWaypointIds = ingameIds)
                        if (updated.selectedA != null && updated.selectedB != null) {
                            updated.copy(pairSummary = computePairSummary(updated))
                        } else {
                            updated
                        }
                    }
                }
        }
    }

    fun toggleSelect(coordId: Long) {
        _uiState.update { state ->
            val next = when {
                state.selectedA == coordId -> state.copy(selectedA = null, selectedB = state.selectedB, pairSummary = null)
                state.selectedB == coordId -> state.copy(selectedB = null, pairSummary = null)
                state.selectedA == null -> state.copy(selectedA = coordId)
                state.selectedB == null -> state.copy(selectedB = coordId)
                else -> state.copy(selectedA = coordId, selectedB = null, pairSummary = null)
            }
            if (next.selectedA != null && next.selectedB != null) {
                next.copy(pairSummary = computePairSummary(next))
            } else {
                next.copy(pairSummary = null)
            }
        }
    }

    private fun computePairSummary(state: CoordsUiState): CoordPairSummary? {
        val a = state.coordinates.find { it.id == state.selectedA } ?: return null
        val b = state.coordinates.find { it.id == state.selectedB } ?: return null
        val dimA = MinecraftDimension.valueOf(a.dimension)
        val dimB = MinecraftDimension.valueOf(b.dimension)
        val result = distanceUseCase(a.x, a.y, a.z, dimA, b.x, b.y, b.z, dimB)
        val fromLabel = a.label.ifBlank { "First" }
        val toLabel = b.label.ifBlank { "Second" }
        val bearingText = if (result.bearingShort != null && result.bearingLong != null) {
            CoordinateFormats.formatBearing(result.bearingShort, result.bearingLong, fromLabel, toLabel)
        } else {
            null
        }
        return CoordPairSummary(
            distanceText = result.formatted,
            bearingText = bearingText,
            alignmentText = result.alignmentText,
        )
    }

    fun showAddDialog() = _uiState.update {
        it.copy(
            showAddDialog = true,
            editingCoordId = null,
            addLabel = "",
            addX = "",
            addY = "64",
            addZ = "",
            addDimension = MinecraftDimension.OVERWORLD,
        )
    }

    fun showEditDialog(coord: CoordinateEntity) {
        val dim = runCatching { MinecraftDimension.valueOf(coord.dimension) }
            .getOrDefault(MinecraftDimension.OVERWORLD)
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingCoordId = coord.id,
                addLabel = coord.label,
                addX = coord.x.toString(),
                addY = coord.y.toString(),
                addZ = coord.z.toString(),
                addDimension = dim,
            )
        }
    }

    fun dismissAddDialog() = _uiState.update {
        it.copy(showAddDialog = false, editingCoordId = null)
    }

    fun updateAddField(
        label: String? = null,
        x: String? = null,
        y: String? = null,
        z: String? = null,
        dimension: MinecraftDimension? = null,
    ) {
        _uiState.update { state ->
            state.copy(
                addLabel = label ?: state.addLabel,
                addX = x ?: state.addX,
                addY = y ?: state.addY,
                addZ = z ?: state.addZ,
                addDimension = dimension ?: state.addDimension,
            )
        }
    }

    fun saveQuickAdd() {
        val state = _uiState.value
        val worldId = state.activeWorld?.id ?: return
        val x = state.addX.toIntOrNull() ?: return
        val y = state.addY.toIntOrNull() ?: return
        val z = state.addZ.toIntOrNull() ?: return
        val label = state.addLabel.trim()
        val editingId = state.editingCoordId
        viewModelScope.launch {
            if (editingId != null) {
                val existing = coordinateRepository.getById(editingId) ?: return@launch
                coordinateRepository.update(
                    existing.copy(
                        label = label,
                        x = x,
                        y = y,
                        z = z,
                        dimension = state.addDimension.name,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
                val displayLabel = label.ifBlank { "Waypoint" }
                timelineRepository.log(
                    worldId = worldId,
                    eventType = "coord_updated",
                    description = "Updated $displayLabel ($x, $y, $z)",
                )
            } else {
                saveCoordinateUseCase(
                    worldId = worldId,
                    x = x,
                    y = y,
                    z = z,
                    dimension = state.addDimension,
                    label = label,
                )
                val displayLabel = label.ifBlank { "Waypoint" }
                timelineRepository.log(
                    worldId = worldId,
                    eventType = "coord_saved",
                    description = "Saved $displayLabel ($x, $y, $z)",
                )
            }
            _uiState.update { current ->
                val cleared = current.copy(
                    showAddDialog = false,
                    editingCoordId = null,
                    addLabel = "",
                    addX = "",
                    addY = "64",
                    addZ = "",
                )
                if (cleared.selectedA != null && cleared.selectedB != null) {
                    cleared.copy(pairSummary = computePairSummary(cleared))
                } else {
                    cleared
                }
            }
        }
    }

    fun copyTp(coord: CoordinateEntity): String {
        val dim = MinecraftDimension.valueOf(coord.dimension)
        return CoordinateFormats.formatTpCommand(coord.x, coord.y, coord.z, dim, coord.label)
    }

    fun copyPlain(coord: CoordinateEntity): String {
        val dim = MinecraftDimension.valueOf(coord.dimension)
        return CoordinateFormats.formatPlainText(coord.x, coord.y, coord.z, dim, coord.label)
    }

    /** Reorder coords list (list indices, not LazyColumn indices). */
    fun moveCoordinateInList(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = _uiState.value.coordinates
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val reordered = current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        _uiState.update { state ->
            val next = state.copy(coordinates = reordered)
            if (next.selectedA != null && next.selectedB != null) {
                next.copy(pairSummary = computePairSummary(next))
            } else {
                next
            }
        }
        viewModelScope.launch {
            coordinateRepository.applySortOrder(reordered.map { it.id })
        }
    }

    fun deleteCoordinate(id: Long) {
        val worldId = _uiState.value.activeWorld?.id ?: return
        viewModelScope.launch {
            val coord = coordinateRepository.getById(id)
            coordinateRepository.delete(id)
            coord?.let {
                val label = it.label.ifBlank { "Coordinate" }
                timelineRepository.log(
                    worldId = worldId,
                    eventType = "coord_deleted",
                    description = "Deleted $label (${it.x}, ${it.y}, ${it.z})",
                )
            }
            _uiState.update { state ->
                val clearedA = if (state.selectedA == id) null else state.selectedA
                val clearedB = if (state.selectedB == id) null else state.selectedB
                val next = state.copy(selectedA = clearedA, selectedB = clearedB)
                if (next.selectedA != null && next.selectedB != null) {
                    next.copy(pairSummary = computePairSummary(next))
                } else {
                    next.copy(pairSummary = null)
                }
            }
        }
    }

    fun updatePortalInputs(x: String, z: String) {
        _uiState.update { it.copy(portalOverworldX = x, portalOverworldZ = z) }
    }

    fun calculatePortal() {
        val x = _uiState.value.portalOverworldX.toIntOrNull() ?: return
        val z = _uiState.value.portalOverworldZ.toIntOrNull() ?: return
        val result = portalCalcUseCase(x, z)
        _uiState.update { it.copy(portalText = result.formatted) }
    }
}
