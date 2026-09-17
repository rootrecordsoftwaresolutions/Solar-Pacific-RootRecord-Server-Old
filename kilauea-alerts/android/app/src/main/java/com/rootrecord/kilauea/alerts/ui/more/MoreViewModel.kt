package com.rootrecord.kilauea.alerts.ui.more

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.DeveloperMessage
import com.rootrecord.kilauea.alerts.data.repository.DeveloperMessagesRepository
import com.rootrecord.kilauea.alerts.data.repository.RootRecordAuthRepository
import com.rootrecord.kilauea.alerts.notifications.KilaueaNotificationManager
import com.google.firebase.messaging.FirebaseMessaging
import com.rootrecord.kilauea.alerts.work.WorkEnqueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val prefs: KilaueaPreferences,
    private val authRepo: RootRecordAuthRepository,
    private val devMessages: DeveloperMessagesRepository,
    private val notifications: KilaueaNotificationManager,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val notifyVolcano = prefs.notificationVolcano.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifyVolcanoBypassDnd = prefs.notificationVolcanoBypassDnd.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyVolcanoAlarmSound = prefs.notificationVolcanoAlarmSound.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyVolcanoElevatedOnly = prefs.notificationVolcanoElevatedOnly.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyNws = prefs.notificationNws.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifyEq = prefs.notificationEq.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyLive = prefs.notificationLiveFeeds.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val eqThreshold = prefs.eqMagnitudeThreshold.stateIn(viewModelScope, SharingStarted.Eagerly, 4f)
    val themeMode = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, KilaueaPreferences.THEME_SYSTEM)
    val fontScale = prefs.fontScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val authSignedIn = prefs.authSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val authEmail = prefs.authEmail.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val authProUnlocked = prefs.authProUnlocked.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    /** Latest team update (or null while loading / on error). Fetched once per VM. */
    private val _latestDevMessage = MutableStateFlow<DeveloperMessage?>(null)
    val latestDevMessage = _latestDevMessage.asStateFlow()

    init {
        viewModelScope.launch {
            devMessages.latest().onSuccess { _latestDevMessage.value = it }
        }
    }

    fun setVolcano(v: Boolean) {
        viewModelScope.launch {
            prefs.setNotifyVolcano(v)
            if (v) WorkEnqueue.enqueueOneShotAlertPoll(appContext)
        }
    }

    fun setVolcanoBypassDnd(v: Boolean) {
        viewModelScope.launch {
            prefs.setNotifyVolcanoBypassDnd(v)
            notifications.ensureChannels()
            if (v) {
                notifications.openDoNotDisturbAccessSettings()
                notifications.openVolcanoUrgentChannelSettings()
            }
        }
    }

    fun setVolcanoAlarmSound(v: Boolean) {
        viewModelScope.launch {
            prefs.setNotifyVolcanoAlarmSound(v)
            notifications.ensureChannels()
            if (v) notifications.openVolcanoUrgentChannelSettings()
        }
    }

    fun setVolcanoElevatedOnly(v: Boolean) {
        viewModelScope.launch {
            prefs.setNotifyVolcanoElevatedOnly(v)
        }
    }

    fun openVolcanoNotificationSettings() {
        notifications.openVolcanoUrgentChannelSettings()
    }

    fun openAppNotificationSettings() {
        notifications.openAppNotificationSettings()
    }

    fun setNws(v: Boolean) {
        viewModelScope.launch {
            if (!prefs.authProUnlocked.first()) return@launch
            prefs.setNotifyNws(v)
        }
    }

    fun setEq(v: Boolean) {
        viewModelScope.launch {
            if (!prefs.authProUnlocked.first()) return@launch
            prefs.setNotifyEq(v)
        }
    }

    fun setLive(v: Boolean) {
        viewModelScope.launch {
            if (!prefs.authProUnlocked.first()) return@launch
            prefs.setNotifyLiveFeeds(v)
        }
    }

    fun setThreshold(v: Float) {
        viewModelScope.launch {
            if (!prefs.authProUnlocked.first()) return@launch
            prefs.setEqThreshold(v.coerceIn(1f, 6f))
            WorkEnqueue.enqueueOneShotAlertPoll(appContext)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { prefs.setFontScale(scale) }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            val r = authRepo.login(email, password)
            _loginBusy.value = false
            r.onSuccess { syncPushTokenAfterAuth() }
            r.onFailure { _loginError.value = it.message ?: "Sign-in failed." }
        }
    }

    fun createAccount(email: String, password: String) {
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            val r = authRepo.createAccount(email, password)
            _loginBusy.value = false
            r.onSuccess { syncPushTokenAfterAuth() }
            r.onFailure { _loginError.value = it.message ?: "Account creation failed." }
        }
    }

    /** Re-bind FCM to the signed-in account so server pushes reach this install. */
    private fun syncPushTokenAfterAuth() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch { authRepo.registerPushToken(token) }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepo.logout() }
    }
}
