package com.rootrecord.kilauea.alerts.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.RootRecordAuthRepository
import com.rootrecord.kilauea.alerts.notifications.KilaueaNotificationManager
import com.rootrecord.kilauea.alerts.work.WorkEnqueue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Server FCM for new USGS notices (10-minute cron) plus manual broadcasts.
 * Marks notice ids seen so opening the app does not re-fire the same alert.
 */
@AndroidEntryPoint
class KilaueaFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var authRepo: RootRecordAuthRepository
    @Inject lateinit var notifications: KilaueaNotificationManager
    @Inject lateinit var prefs: KilaueaPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty() && message.notification == null) return
        serviceScope.launch {
            notifications.ensureChannels()
            val data = message.data
            val noticeId = data["notice_id"]?.trim().orEmpty()
            if (noticeId.isNotEmpty()) {
                prefs.addNotifiedVolcanoId(noticeId)
            }
            val volcanoOn = prefs.notificationVolcano.first()
            val title = message.notification?.title ?: data["title"]
            val body = message.notification?.body ?: data["body"]
            if (volcanoOn && !title.isNullOrBlank() && !body.isNullOrBlank()) {
                val urgent = data["urgent"]?.equals("true", ignoreCase = true) == true
                notifications.notifyVolcano(title, body, urgent = urgent)
            }
            WorkEnqueue.enqueueOneShotAlertPoll(applicationContext)
        }
    }

    override fun onNewToken(token: String) {
        serviceScope.launch {
            authRepo.registerPushToken(token)
        }
    }
}
