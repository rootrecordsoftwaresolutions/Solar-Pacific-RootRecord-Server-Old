package com.rootrecord.kilauea.alerts.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rootrecord.kilauea.alerts.work.WorkEnqueue

/** Re-enqueue USGS alert polling after device reboot (WorkManager schedules do not always survive). */
class BootAlertPollReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        WorkEnqueue.enqueueOneShotAlertPoll(context.applicationContext)
    }
}
