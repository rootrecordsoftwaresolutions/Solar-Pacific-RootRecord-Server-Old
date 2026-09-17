package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.RootMcAccountSyncRepository
import com.rootrecord.rootmc.data.repository.SnapshotRepository
import com.rootrecord.rootmc.data.sync.AccountSyncDirection
import com.rootrecord.rootmc.data.sync.AccountSyncResult
import com.rootrecord.rootmc.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncAccountDataUseCase @Inject constructor(
    private val prefs: RootMcPreferences,
    private val snapshotRepository: SnapshotRepository,
    private val accountSyncRepository: RootMcAccountSyncRepository,
    private val syncDedicatedServer: SyncDedicatedServerUseCase,
    private val syncIngameEvents: SyncIngameEventsUseCase,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun syncIfSignedIn(): Result<AccountSyncResult?> = withContext(io) {
        if (!prefs.authSignedIn.first()) return@withContext Result.success(null)
        runCatching {
            syncDedicatedServer.sync()
            syncIngameEvents.sync()
            val local = snapshotRepository.exportSnapshot()
            val remote = accountSyncRepository.fetchRemoteSnapshot().getOrThrow()
            val localRev = maxOf(local.exportedAt, local.maxContentRevision())
            val remoteRev = remote?.let {
                maxOf(it.updatedAt, it.snapshot.exportedAt, it.snapshot.maxContentRevision())
            } ?: 0L
            val lastSyncedAt = prefs.getCloudSyncUpdatedAt()

            val outcome = when {
                remote == null && local.hasUserContent() -> {
                    val updatedAt = accountSyncRepository.pushSnapshot(local).getOrThrow()
                    AccountSyncResult(AccountSyncDirection.PushedToCloud, updatedAt)
                }
                remote == null -> {
                    AccountSyncResult(AccountSyncDirection.UpToDate, 0L)
                }
                !local.hasUserContent() && remoteRev > 0L -> {
                    snapshotRepository.importSnapshot(remote.snapshot)
                    AccountSyncResult(AccountSyncDirection.PulledFromCloud, remoteRev)
                }
                remoteRev > localRev -> {
                    snapshotRepository.importSnapshot(remote.snapshot)
                    AccountSyncResult(AccountSyncDirection.PulledFromCloud, remoteRev)
                }
                localRev > remoteRev || localRev > lastSyncedAt -> {
                    val updatedAt = accountSyncRepository.pushSnapshot(local).getOrThrow()
                    AccountSyncResult(AccountSyncDirection.PushedToCloud, updatedAt)
                }
                else -> AccountSyncResult(AccountSyncDirection.UpToDate, remoteRev)
            }
            prefs.setCloudSyncUpdatedAt(outcome.cloudUpdatedAt.coerceAtLeast(prefs.getCloudSyncUpdatedAt()))
            prefs.setCloudSyncError(null)
            outcome
        }.onFailure { error ->
            prefs.setCloudSyncError(error.message ?: "Sync failed")
        }
    }
}
