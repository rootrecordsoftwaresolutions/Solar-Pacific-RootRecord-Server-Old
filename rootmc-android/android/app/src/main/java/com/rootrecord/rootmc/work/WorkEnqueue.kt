package com.rootrecord.rootmc.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkEnqueue {

    fun schedulePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("rootmc_reference_refresh")
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        wm.enqueueUniquePeriodicWork(
            "rootmc_account_sync_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AccountSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )
    }
}
