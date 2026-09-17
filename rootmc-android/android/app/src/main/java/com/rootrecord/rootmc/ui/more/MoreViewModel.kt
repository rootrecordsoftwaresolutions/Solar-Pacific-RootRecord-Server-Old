package com.rootrecord.rootmc.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.BuildConfig
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.ExportRepository
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

data class MoreUiState(
    val themeMode: String = RootMcPreferences.THEME_SYSTEM,
    val analyticsOptIn: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val accountId: String? = null,
    val guestId: String = "",
    val membershipTier: MembershipTier = MembershipTier.Guest,
    val membershipLabel: String = MembershipTier.Guest.label(),
    val adsRemoved: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
    val minecraftLinked: Boolean = false,
    val minecraftUsername: String? = null,
    val cloudSyncUpdatedAt: Long = 0L,
    val cloudSyncError: String? = null,
    val sessionExpiresLabel: String? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val prefs: RootMcPreferences,
    private val exportRepository: ExportRepository,
    private val authRepository: RootRecordAuthRepository,
    private val serverRepository: ServerRepository,
    private val accountSyncScheduler: AccountSyncScheduler,
    private val syncAccountData: SyncAccountDataUseCase,
    private val pushRegistrar: RootMcPushRegistrar,
) : ViewModel() {

    private val serverMembership = MutableStateFlow<ServerMembership?>(null)

    val uiState: StateFlow<MoreUiState> = combine(
        combine(
            prefs.themeMode,
            prefs.analyticsOptIn,
            prefs.authSignedIn,
            prefs.authEmail,
            prefs.authAccountId,
        ) { theme, analytics, signedIn, email, accountId ->
            arrayOf(theme, analytics, signedIn, email, accountId)
        },
        combine(
            prefs.guestId,
            prefs.membershipTier,
            prefs.authProUnlocked,
        ) { guestId, tier, adsRemoved ->
            arrayOf(guestId, tier, adsRemoved)
        },
        combine(
            prefs.cloudSyncUpdatedAt,
            prefs.cloudSyncError,
            prefs.authSessionExpiresAt,
            serverMembership,
        ) { cloudSyncUpdatedAt, cloudSyncError, sessionExpiresAt, membership ->
            arrayOf(cloudSyncUpdatedAt, cloudSyncError, sessionExpiresAt, membership)
        },
    ) { accountBits, membershipBits, syncBits ->
        val theme = accountBits[0] as String
        val analytics = accountBits[1] as Boolean
        val signedIn = accountBits[2] as Boolean
        val email = accountBits[3] as String?
        val accountId = accountBits[4] as String?
        val guestId = membershipBits[0] as String
        val tier = membershipBits[1] as MembershipTier
        val adsRemoved = membershipBits[2] as Boolean
        val cloudSyncUpdatedAt = syncBits[0] as Long
        val cloudSyncError = syncBits[1] as String?
        val sessionExpiresAt = syncBits[2] as Long?
        val membership = syncBits[3] as ServerMembership?
        MoreUiState(
            themeMode = theme,
            analyticsOptIn = analytics,
            signedIn = signedIn,
            email = email,
            accountId = accountId,
            guestId = guestId,
            membershipTier = tier,
            membershipLabel = tier.label(),
            adsRemoved = adsRemoved,
            minecraftLinked = membership?.minecraftLinked == true,
            minecraftUsername = membership?.minecraftUsername,
            cloudSyncUpdatedAt = cloudSyncUpdatedAt,
            cloudSyncError = cloudSyncError,
            sessionExpiresLabel = com.rootrecord.rootmc.ui.auth.formatSessionExpires(sessionExpiresAt),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

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

    init {
        viewModelScope.launch {
            prefs.ensureGuestId()
            prefs.clearMinecraftProfile()
            refreshServerMembership()
        }
    }

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

    fun cycleTheme(current: String) {
        viewModelScope.launch {
            val next = when (current) {
                RootMcPreferences.THEME_LIGHT -> RootMcPreferences.THEME_DARK
                RootMcPreferences.THEME_DARK -> RootMcPreferences.THEME_SYSTEM
                else -> RootMcPreferences.THEME_LIGHT
            }
            prefs.setThemeMode(next)
        }
    }

    fun setAnalyticsOptIn(enabled: Boolean) {
        viewModelScope.launch { prefs.setAnalyticsOptIn(enabled) }
    }

    fun exportJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { exportRepository.exportJson() }.onSuccess(onReady)
        }
    }

    fun exportMarkdown(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { exportRepository.exportMarkdown() }.onSuccess(onReady)
        }
    }

    fun clearLoginError() {
        _loginError.value = null
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

    fun signInWithLinkCode(code: String) {
        viewModelScope.launch {
            _codeBusy.value = true
            _codeError.value = null
            val result = authRepository.loginWithLinkCode(code)
            _codeBusy.value = false
            result.onSuccess {
                authRepository.refreshAccountAccess()
                accountSyncScheduler.requestSync()
                pushRegistrar.registerCurrentTokenAsync()
                refreshServerMembership()
            }
            result.onFailure { _codeError.value = it.message ?: "Could not sign in with code." }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            val result = authRepository.login(email, password)
            _loginBusy.value = false
            result.onSuccess {
                authRepository.refreshAccountAccess()
                accountSyncScheduler.requestSync()
                pushRegistrar.registerCurrentTokenAsync()
                refreshServerMembership()
            }
            result.onFailure { _loginError.value = it.message ?: "Sign-in failed." }
        }
    }

    fun createAccount(email: String, password: String) {
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            val result = authRepository.createAccount(email, password)
            _loginBusy.value = false
            result.onSuccess {
                authRepository.refreshAccountAccess()
                accountSyncScheduler.requestSync()
                pushRegistrar.registerCurrentTokenAsync()
                refreshServerMembership()
            }
            result.onFailure { _loginError.value = it.message ?: "Account creation failed." }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            syncAccountData.syncIfSignedIn()
            authRepository.logout()
            serverMembership.value = null
        }
    }

    fun syncNow() {
        viewModelScope.launch { syncAccountData.syncIfSignedIn() }
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
}

const val DISCORD_SUPPORT_URL = "https://discord.gg/rFFQYrNaqS"
