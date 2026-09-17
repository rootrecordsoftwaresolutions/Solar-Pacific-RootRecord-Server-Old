package com.rootrecord.rootmc.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.RootRecordAuthRepository
import com.rootrecord.rootmc.data.repository.ServerMembership
import com.rootrecord.rootmc.data.repository.ServerRepository
import com.rootrecord.rootmc.fcm.RootMcPushRegistrar
import com.rootrecord.rootmc.domain.model.MembershipTier
import com.rootrecord.rootmc.domain.model.label
import com.rootrecord.rootmc.domain.usecase.SyncAccountDataUseCase
import com.rootrecord.rootmc.sync.AccountSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val signedIn: Boolean = false,
    val emailDisplay: String? = null,
    val accountId: String? = null,
    val membershipTier: MembershipTier = MembershipTier.Guest,
    val membershipLabel: String = MembershipTier.Guest.label(),
    val minecraftLinked: Boolean = false,
    val minecraftUsername: String? = null,
    val sessionExpiresLabel: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: RootRecordAuthRepository,
    private val serverRepository: ServerRepository,
    private val accountSyncScheduler: AccountSyncScheduler,
    private val syncAccountData: SyncAccountDataUseCase,
    private val pushRegistrar: RootMcPushRegistrar,
    private val prefs: RootMcPreferences,
) : ViewModel() {

    private val serverMembership = MutableStateFlow<ServerMembership?>(null)

    val uiState: StateFlow<AuthUiState> = combine(
        combine(
            prefs.authSignedIn,
            prefs.authEmail,
            prefs.authAccountId,
            prefs.membershipTier,
            prefs.authSessionExpiresAt,
        ) { signedIn, mail, accountId, tier, sessionExpiresAt ->
            arrayOf(signedIn, mail, accountId, tier, sessionExpiresAt)
        },
        serverMembership,
    ) { authBits, membership ->
        val signedIn = authBits[0] as Boolean
        val mail = authBits[1] as String?
        val accountId = authBits[2] as String?
        val tier = authBits[3] as MembershipTier
        val sessionExpiresAt = authBits[4] as Long?
        AuthUiState(
            emailDisplay = mail,
            signedIn = signedIn,
            accountId = accountId,
            membershipTier = tier,
            membershipLabel = tier.label(),
            minecraftLinked = membership?.minecraftLinked == true,
            minecraftUsername = membership?.minecraftUsername,
            sessionExpiresLabel = formatSessionExpires(sessionExpiresAt),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState())

    val signedIn: StateFlow<Boolean> = prefs.authSignedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _discordBusy = MutableStateFlow(false)
    val discordBusy: StateFlow<Boolean> = _discordBusy.asStateFlow()

    private val _discordError = MutableStateFlow<String?>(null)
    val discordError: StateFlow<String?> = _discordError.asStateFlow()

    private val _codeBusy = MutableStateFlow(false)
    val codeBusy: StateFlow<Boolean> = _codeBusy.asStateFlow()

    private val _codeError = MutableStateFlow<String?>(null)
    val codeError: StateFlow<String?> = _codeError.asStateFlow()

    private val _codePreviewUsername = MutableStateFlow<String?>(null)
    val codePreviewUsername: StateFlow<String?> = _codePreviewUsername.asStateFlow()

    private var previewJob: Job? = null

    fun refreshServerMembership() {
        viewModelScope.launch {
            if (!prefs.authSignedIn.first()) {
                serverMembership.value = null
                return@launch
            }
            serverRepository.fetchMembership()
                .onSuccess { serverMembership.value = it }
                .onFailure { serverMembership.value = null }
        }
    }

    fun clearDiscordError() {
        _discordError.value = null
    }

    fun clearCodeError() {
        _codeError.value = null
    }

    fun onLinkCodeChanged(code: String) {
        previewJob?.cancel()
        if (code.length != 6) {
            _codePreviewUsername.value = null
            return
        }
        previewJob = viewModelScope.launch {
            delay(350)
            authRepository.previewLinkCode(code)
                .onSuccess { preview ->
                    _codePreviewUsername.value = if (preview.valid) preview.minecraftUsername else null
                }
                .onFailure { _codePreviewUsername.value = null }
        }
    }

    fun signInWithLinkCode(code: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _codeBusy.value = true
            _codeError.value = null
            val result = authRepository.loginWithLinkCode(code)
            _codeBusy.value = false
            result.onSuccess {
                accountSyncScheduler.requestSync()
                pushRegistrar.registerCurrentTokenAsync()
                refreshServerMembership()
                onSuccess()
            }
            result.onFailure { _codeError.value = it.message ?: "Could not sign in with code." }
        }
    }

    fun startDiscordLink(code: String, onAuthorizeUrl: (String) -> Unit) {
        viewModelScope.launch {
            _discordBusy.value = true
            _discordError.value = null
            val result = authRepository.startDiscordLink(code)
            _discordBusy.value = false
            result.onSuccess(onAuthorizeUrl)
            result.onFailure { _discordError.value = it.message ?: "Could not start Discord link." }
        }
    }

    fun startDiscordSignIn(onAuthorizeUrl: (String) -> Unit) {
        viewModelScope.launch {
            _discordBusy.value = true
            _discordError.value = null
            val result = authRepository.startDiscordSignIn()
            _discordBusy.value = false
            result.onSuccess(onAuthorizeUrl)
            result.onFailure { _discordError.value = it.message ?: "Could not start Discord sign-in." }
        }
    }

    fun onDiscordOAuthComplete() {
        viewModelScope.launch {
            authRepository.refreshAccountAccess()
            accountSyncScheduler.requestSync()
            pushRegistrar.registerCurrentTokenAsync()
            refreshServerMembership()
        }
    }

    fun logout() {
        viewModelScope.launch {
            syncAccountData.syncIfSignedIn()
            authRepository.logout()
            serverMembership.value = null
        }
    }
}

internal fun formatSessionExpires(expiresAtMs: Long?): String? {
    if (expiresAtMs == null || expiresAtMs <= 0L) return null
    val remaining = expiresAtMs - System.currentTimeMillis()
    if (remaining <= 0L) return null
    val days = (remaining / (24 * 60 * 60 * 1000L)).toInt()
    return if (days >= 1) {
        "Session · about ${days}d left"
    } else {
        val hours = (remaining / (60 * 60 * 1000L)).toInt().coerceAtLeast(1)
        "Session · about ${hours}h left"
    }
}
