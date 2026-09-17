package com.rootrecord.rootmc.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rootrecord.rootmc.data.repository.ReferenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Stub periodic job to check for newer reference DB version on the Worker. */
@HiltWorker
class ReferenceRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val referenceRepository: ReferenceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        referenceRepository.checkForUpdates()
        return Result.success()
    }
}
