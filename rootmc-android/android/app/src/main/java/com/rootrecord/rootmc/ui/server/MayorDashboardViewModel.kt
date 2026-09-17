package com.rootrecord.rootmc.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.MayorTownSnapshot
import com.rootrecord.rootmc.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MayorDashboardUiState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val minecraftLinked: Boolean = false,
    val isMayor: Boolean = false,
    val town: MayorTownSnapshot? = null,
    val error: String? = null,
)

@HiltViewModel
class MayorDashboardViewModel @Inject constructor(
    private val prefs: RootMcPreferences,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MayorDashboardUiState())
    val uiState: StateFlow<MayorDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val signedIn = prefs.authSignedIn.first()
            if (!signedIn) {
                _uiState.update { it.copy(loading = false, signedIn = false) }
                return@launch
            }
            val config = serverRepository.fetchFeaturedServerConfig().getOrNull()
            val serverId = config?.serverId ?: "rootmc"
            val membership = serverRepository.fetchMembership().getOrNull()
            val linked = membership?.minecraftLinked == true
            if (!linked) {
                _uiState.update {
                    it.copy(loading = false, signedIn = true, minecraftLinked = false)
                }
                return@launch
            }
            serverRepository.fetchMayorTown(serverId)
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            signedIn = true,
                            minecraftLinked = true,
                            isMayor = snapshot.isMayor,
                            town = snapshot,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            signedIn = true,
                            minecraftLinked = linked,
                            error = err.message ?: "Could not load town data.",
                        )
                    }
                }
        }
    }
}
