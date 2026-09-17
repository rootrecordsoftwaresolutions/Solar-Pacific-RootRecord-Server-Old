package com.rootrecord.rootmc.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.AreaEntity
import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.repository.AreaRepository
import com.rootrecord.rootmc.data.repository.TimelineRepository
import com.rootrecord.rootmc.data.repository.WorldRepository
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

data class AreasUiState(
    val activeWorld: WorldEntity? = null,
    val areas: List<AreaEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val addLabel: String = "",
    val corner1X: String = "",
    val corner1Z: String = "",
    val corner2X: String = "",
    val corner2Z: String = "",
    val addDimension: MinecraftDimension = MinecraftDimension.OVERWORLD,
)

@HiltViewModel
class AreasViewModel @Inject constructor(
    worldRepository: WorldRepository,
    private val areaRepository: AreaRepository,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AreasUiState())
    val uiState: StateFlow<AreasUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            worldRepository.observeActiveWorld().collect { world ->
                _uiState.update { it.copy(activeWorld = world) }
            }
        }
        viewModelScope.launch {
            worldRepository.observeActiveWorld()
                .flatMapLatest { world ->
                    if (world == null) flowOf(emptyList())
                    else areaRepository.observeByWorld(world.id)
                }
                .collect { areas ->
                    _uiState.update { it.copy(areas = areas) }
                }
        }
    }

    fun showAddDialog() = _uiState.update {
        it.copy(
            showAddDialog = true,
            addLabel = "",
            corner1X = "",
            corner1Z = "",
            corner2X = "",
            corner2Z = "",
            addDimension = MinecraftDimension.OVERWORLD,
        )
    }

    fun dismissAddDialog() = _uiState.update { it.copy(showAddDialog = false) }

    fun updateAddField(
        label: String? = null,
        c1x: String? = null,
        c1z: String? = null,
        c2x: String? = null,
        c2z: String? = null,
        dimension: MinecraftDimension? = null,
    ) {
        _uiState.update { state ->
            state.copy(
                addLabel = label ?: state.addLabel,
                corner1X = c1x ?: state.corner1X,
                corner1Z = c1z ?: state.corner1Z,
                corner2X = c2x ?: state.corner2X,
                corner2Z = c2z ?: state.corner2Z,
                addDimension = dimension ?: state.addDimension,
            )
        }
    }

    fun saveArea() {
        val state = _uiState.value
        val worldId = state.activeWorld?.id ?: return
        val x1 = state.corner1X.toIntOrNull() ?: return
        val z1 = state.corner1Z.toIntOrNull() ?: return
        val x2 = state.corner2X.toIntOrNull() ?: return
        val z2 = state.corner2Z.toIntOrNull() ?: return
        viewModelScope.launch {
            areaRepository.saveFromTwoCorners(
                worldId = worldId,
                x1 = x1,
                z1 = z1,
                x2 = x2,
                z2 = z2,
                dimension = state.addDimension,
                label = state.addLabel.trim(),
            )
            timelineRepository.log(
                worldId = worldId,
                eventType = "area_saved",
                description = "Saved area ${state.addLabel.ifBlank { "($x1,$z1)–($x2,$z2)" }}",
            )
            _uiState.update { it.copy(showAddDialog = false) }
        }
    }

    fun deleteArea(id: Long) {
        val worldId = _uiState.value.activeWorld?.id ?: return
        viewModelScope.launch {
            val area = areaRepository.getById(id)
            areaRepository.delete(id)
            area?.let {
                timelineRepository.log(
                    worldId = worldId,
                    eventType = "area_deleted",
                    description = "Deleted area ${it.label}",
                )
            }
        }
    }
}
