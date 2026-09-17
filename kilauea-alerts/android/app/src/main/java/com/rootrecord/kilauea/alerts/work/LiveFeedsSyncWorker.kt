package com.rootrecord.kilauea.alerts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rootrecord.kilauea.alerts.data.repository.LiveFeedsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LiveFeedsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val liveFeedsRepository: LiveFeedsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        liveFeedsRepository.refreshMergedCatalog()
        return Result.success()
    }
}
