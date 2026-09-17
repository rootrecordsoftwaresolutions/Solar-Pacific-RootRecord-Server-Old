package com.rootrecord.kilauea.alerts.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.rootrecord.kilauea.alerts.MainActivity
import com.rootrecord.kilauea.alerts.R
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KilaueaNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: KilaueaPreferences,
) {

    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    suspend fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val bypassDnd = prefs.notificationVolcanoBypassDnd.first()
        val alarmSound = prefs.notificationVolcanoAlarmSound.first()
        listOf(
            Triple(CH_VOLCANO, "Kīlauea — USGS volcano", NotificationManager.IMPORTANCE_HIGH),
            Triple(CH_NWS, "Big Island — NWS alerts", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_EQ, "Hawaiʻi earthquakes", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_LIVE, "Live feeds", NotificationManager.IMPORTANCE_LOW),
        ).forEach { (id, name, imp) ->
            nm.createNotificationChannel(android.app.NotificationChannel(id, name, imp))
        }
        val urgent = android.app.NotificationChannel(
            CH_VOLCANO_URGENT,
            "Kīlauea — urgent volcano alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Orange/red USGS notices and eruptive activity — can use alarm sound and bypass Do Not Disturb when allowed in system settings."
            enableVibration(true)
            setBypassDnd(bypassDnd)
            val soundUri = if (alarmSound) {
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            } else {
                Settings.System.DEFAULT_NOTIFICATION_URI
            }
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(
                        if (alarmSound) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION,
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(urgent)
    }

    suspend fun notifyVolcano(
        title: String,
        text: String,
        urgent: Boolean = false,
        notificationId: Int = NOTIF_VOLCANO,
    ) {
        ensureChannels()
        val bypassDnd = prefs.notificationVolcanoBypassDnd.first()
        val alarmSound = prefs.notificationVolcanoAlarmSound.first()
        val useUrgentChannel = urgent && (bypassDnd || alarmSound)
        notify(
            channel = if (useUrgentChannel) CH_VOLCANO_URGENT else CH_VOLCANO,
            id = notificationId,
            title = title,
            text = text,
            priority = if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH,
            category = if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS,
        )
    }

    fun notifyNws(title: String, text: String, notificationId: Int = NOTIF_NWS) {
        notify(CH_NWS, notificationId, title, text)
    }

    fun notifyEarthquake(title: String, text: String, notificationId: Int = NOTIF_EQ) {
        notify(CH_EQ, notificationId, title, text)
    }

    fun notifyLiveFeed(title: String, text: String) {
        notify(CH_LIVE, NOTIF_LIVE, title, text)
    }

    fun openVolcanoUrgentChannelSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openAppNotificationSettings()
            return
        }
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CH_VOLCANO_URGENT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun openAppNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun openDoNotDisturbAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } else {
            openAppNotificationSettings()
        }
    }

    private fun notify(
        channel: String,
        id: Int,
        title: String,
        text: String,
        priority: Int = NotificationCompat.PRIORITY_HIGH,
        category: String = NotificationCompat.CATEGORY_STATUS,
    ) {
        val pending = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ALERTS)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setCategory(category)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    companion object {
        const val CH_VOLCANO = "kilauea_volcano"
        const val CH_VOLCANO_URGENT = "kilauea_volcano_urgent"
        const val CH_NWS = "kilauea_nws"
        const val CH_EQ = "kilauea_eq"
        const val CH_LIVE = "kilauea_live"

        private const val NOTIF_VOLCANO = 1001
        private const val NOTIF_NWS = 1002
        private const val NOTIF_EQ = 1003
        private const val NOTIF_LIVE = 1004
    }
}
