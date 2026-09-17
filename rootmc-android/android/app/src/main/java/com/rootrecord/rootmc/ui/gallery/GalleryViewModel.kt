package com.rootrecord.rootmc.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.dao.MediaDao
import com.rootrecord.rootmc.data.local.entity.MediaAttachmentEntity
import com.rootrecord.rootmc.data.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    worldRepository: WorldRepository,
    mediaDao: MediaDao,
) : ViewModel() {

    val media: StateFlow<List<MediaAttachmentEntity>> = worldRepository.observeActiveWorld()
        .flatMapLatest { world ->
            if (world == null) flowOf(emptyList()) else mediaDao.observeByWorld(world.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
