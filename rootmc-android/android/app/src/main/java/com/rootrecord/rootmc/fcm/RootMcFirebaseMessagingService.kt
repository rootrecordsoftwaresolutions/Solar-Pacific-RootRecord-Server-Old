package com.rootrecord.rootmc.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rootrecord.rootmc.data.repository.RootRecordAuthRepository
import com.rootrecord.rootmc.notifications.RootMcNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** FCM for shop price alerts and future RootMC push (api.rootmc.net cron). */
@AndroidEntryPoint
class RootMcFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var authRepo: RootRecordAuthRepository
    @Inject lateinit var notifications: RootMcNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty() && message.notification == null) return
        serviceScope.launch {
            notifications.ensureChannels()
            val data = message.data
            val title = message.notification?.title ?: data["title"]
            val body = message.notification?.body ?: data["body"]
            if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
                notifications.notifyShopAlert(title, body)
            }
        }
    }

    override fun onNewToken(token: String) {
        serviceScope.launch {
            authRepo.registerPushToken(token).onFailure { err ->
                Log.w(TAG, "push token register failed: ${err.message}")
            }
        }
    }

    private companion object {
        const val TAG = "RootMcFcm"
    }
}
