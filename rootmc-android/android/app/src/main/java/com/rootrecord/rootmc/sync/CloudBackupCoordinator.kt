package com.rootrecord.rootmc.sync

import javax.inject.Inject
import javax.inject.Singleton

/** Queues a D1 snapshot upload after local Block Notes data changes. */
@Singleton
class CloudBackupCoordinator @Inject constructor(
    private val accountSyncScheduler: AccountSyncScheduler,
) {
    fun scheduleBackupAfterLocalChange() {
        accountSyncScheduler.requestSync()
    }
}
