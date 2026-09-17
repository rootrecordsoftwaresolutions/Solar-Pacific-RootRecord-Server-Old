package com.rootrecord.rootmc.ui.realm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.RealmFriend
import com.rootrecord.rootmc.data.repository.RealmGroup
import com.rootrecord.rootmc.data.repository.RealmGroupLimits
import com.rootrecord.rootmc.data.repository.RealmPlayerProfile
import com.rootrecord.rootmc.data.repository.RealmRepository
import com.rootrecord.rootmc.data.repository.RealmUserSearchResult
import com.rootrecord.rootmc.domain.usecase.SyncRealmProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealmSocialUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val profile: RealmPlayerProfile? = null,
    val friends: List<RealmFriend> = emptyList(),
    val groups: List<RealmGroup> = emptyList(),
    val groupLimits: RealmGroupLimits = RealmGroupLimits(1, 0),
    val searchResults: List<RealmUserSearchResult> = emptyList(),
    val realmUsernameField: String = "",
    val bioField: String = "",
    val newGroupName: String = "",
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class RealmSocialViewModel @Inject constructor(
    private val prefs: RootMcPreferences,
    private val realmRepository: RealmRepository,
    private val syncRealmProfile: SyncRealmProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RealmSocialUiState())
    val uiState: StateFlow<RealmSocialUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val signedIn = prefs.authSignedIn.first()
            _uiState.update { it.copy(signedIn = signedIn, loading = signedIn, error = null) }
            if (!signedIn) {
                _uiState.update { it.copy(loading = false, error = "Sign in to use Realm friends and groups.") }
                return@launch
            }
            val profileResult = realmRepository.fetchMyProfile()
            val friendsResult = realmRepository.listFriends()
            val groupsResult = realmRepository.listGroups()
            profileResult.onSuccess { (profile, _) ->
                _uiState.update {
                    it.copy(
                        profile = profile,
                        realmUsernameField = profile?.realmUsername.orEmpty(),
                        bioField = profile?.bio.orEmpty(),
                    )
                }
            }
            friendsResult.onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
            groupsResult.onSuccess { (groups, limits) ->
                _uiState.update { it.copy(groups = groups, groupLimits = limits) }
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun updateRealmUsername(value: String) = _uiState.update { it.copy(realmUsernameField = value, message = null) }
    fun updateBio(value: String) = _uiState.update { it.copy(bioField = value, message = null) }
    fun updateNewGroupName(value: String) = _uiState.update { it.copy(newGroupName = value) }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            syncRealmProfile.sync(
                realmUsername = _uiState.value.realmUsernameField.ifBlank { null },
                bio = _uiState.value.bioField.ifBlank { null },
            ).onSuccess {
                _uiState.update { it.copy(loading = false, message = "Profile saved to Realm.") }
                refresh()
            }.onFailure { err ->
                _uiState.update { it.copy(loading = false, error = err.message) }
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            realmRepository.searchUsers(query)
                .onSuccess { results -> _uiState.update { it.copy(searchResults = results) } }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun sendFriendRequest(realmUsername: String) {
        viewModelScope.launch {
            realmRepository.sendFriendRequest(realmUsername)
                .onSuccess {
                    _uiState.update { it.copy(message = "Friend request sent.", searchResults = emptyList()) }
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }

    fun createGroup() {
        val name = _uiState.value.newGroupName.trim()
        if (name.length < 2) return
        viewModelScope.launch {
            realmRepository.createGroup(name)
                .onSuccess {
                    _uiState.update { it.copy(newGroupName = "", message = "Group created.") }
                    refresh()
                }
                .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
        }
    }
}
