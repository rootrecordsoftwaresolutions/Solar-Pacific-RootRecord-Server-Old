package com.rootrecord.kilauea.alerts.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val PREFS_ENQUEUE = "kilauea_work_enqueue"
private const val KEY_LAST_FG_ALERT_POLL_MS = "last_fg_alert_poll_ms"

/** Cooldown between foreground-triggered one-shot polls (WorkManager periodic floor is 15 min). */
private const val FOREGROUND_ALERT_POLL_COOLDOWN_MS = 5 * 60 * 1000L

object WorkEnqueue {

    /**
     * Background polling for USGS/NWS/earthquakes.
     * **Note:** Android WorkManager enforces a **15-minute minimum** interval for periodic work;
     * we also run extra [enqueueOneShotAlertPoll] when the app is foreground (see [enqueueAlertPollIfDue]).
     */
    fun schedulePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        wm.enqueueUniquePeriodicWork(
            "kilauea_alert_poll",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AlertPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            "kilauea_live_feeds",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LiveFeedsSyncWorker>(8, TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            "kilauea_home_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HomeRefreshWorker>(45, TimeUnit.MINUTES)
                .setConstraints(network)
                .build(),
        )
    }

    fun enqueueOneShotAlertPoll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val request = androidx.work.OneTimeWorkRequestBuilder<AlertPollWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        wm.enqueue(request)
    }

    /** Cold start / boot — always poll once (not subject to foreground throttle). */
    fun enqueueAlertPollOnLaunch(context: Context) {
        enqueueOneShotAlertPoll(context.applicationContext)
    }

    /** One-shot poll when app is active, throttled — complements 15-min periodic minimum. */
    fun enqueueAlertPollIfDue(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS_ENQUEUE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = sp.getLong(KEY_LAST_FG_ALERT_POLL_MS, 0L)
        if (now - last < FOREGROUND_ALERT_POLL_COOLDOWN_MS) return
        sp.edit().putLong(KEY_LAST_FG_ALERT_POLL_MS, now).apply()
        enqueueOneShotAlertPoll(app)
    }
}
