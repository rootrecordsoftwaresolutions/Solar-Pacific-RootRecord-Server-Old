package com.rootrecord.rootmc.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rootrecord.rootmc.domain.usecase.SyncAccountDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Uploads or downloads the signed-in user's Block Notes snapshot. */
@HiltWorker
class AccountSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncAccountData: SyncAccountDataUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        syncAccountData.syncIfSignedIn().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
}
