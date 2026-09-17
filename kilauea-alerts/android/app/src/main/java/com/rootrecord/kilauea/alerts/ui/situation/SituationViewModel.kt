package com.rootrecord.kilauea.alerts.ui.situation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.repository.KilaueaSituation
import com.rootrecord.kilauea.alerts.data.repository.SituationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SituationUiState(
    val loading: Boolean = false,
    val situation: KilaueaSituation? = null,
    val error: String? = null,
)

@HiltViewModel
class SituationViewModel @Inject constructor(
    private val repo: SituationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SituationUiState())
    val state = _state.asStateFlow()

    init {
        load(force = false)
    }

    fun load(force: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val r = repo.offlineFirst(force)
            _state.update {
                it.copy(
                    loading = false,
                    situation = r.getOrNull(),
                    error = r.exceptionOrNull()?.message,
                )
            }
        }
    }
}
