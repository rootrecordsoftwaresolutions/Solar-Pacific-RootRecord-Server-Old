package com.rootrecord.rootmc.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.entity.NotebookEntity
import com.rootrecord.rootmc.data.local.entity.WorldEntity
import com.rootrecord.rootmc.data.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class NotesUiState(
    val activeWorld: WorldEntity? = null,
    val notebooks: List<NotebookEntity> = emptyList(),
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    worldRepository: WorldRepository,
) : ViewModel() {

    val uiState: StateFlow<NotesUiState> = worldRepository.observeActiveWorld()
        .flatMapLatest { world ->
            if (world == null) {
                flowOf(NotesUiState())
            } else {
                worldRepository.observeNotebooks(world.id).combine(
                    worldRepository.observeActiveWorld(),
                ) { notebooks, active ->
                    NotesUiState(activeWorld = active, notebooks = notebooks)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())
}
