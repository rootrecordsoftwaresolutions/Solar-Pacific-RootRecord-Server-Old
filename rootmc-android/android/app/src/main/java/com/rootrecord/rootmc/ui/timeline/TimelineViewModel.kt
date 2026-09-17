package com.rootrecord.rootmc.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.dao.TimelineDao
import com.rootrecord.rootmc.data.local.entity.ProjectTimelineEventEntity
import com.rootrecord.rootmc.data.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    worldRepository: WorldRepository,
    timelineDao: TimelineDao,
) : ViewModel() {

    val events: StateFlow<List<ProjectTimelineEventEntity>> = worldRepository.observeActiveWorld()
        .flatMapLatest { world ->
            if (world == null) flowOf(emptyList()) else timelineDao.observeByWorld(world.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
