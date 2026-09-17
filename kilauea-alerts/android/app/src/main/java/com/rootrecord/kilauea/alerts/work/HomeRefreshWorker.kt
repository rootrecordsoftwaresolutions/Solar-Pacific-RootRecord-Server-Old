package com.rootrecord.kilauea.alerts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rootrecord.kilauea.alerts.data.repository.UsgVolcanoRepository
import com.rootrecord.kilauea.alerts.data.repository.WeatherRepository
import com.rootrecord.kilauea.alerts.domain.BigIslandLocation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodically refreshes summit-area dashboard + volcano JSON for Home teaser widgets. */
@HiltWorker
class HomeRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val volcanoRepo: UsgVolcanoRepository,
    private val weatherRepo: WeatherRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        volcanoRepo.refreshVolcanoStatus()
        val v = BigIslandLocation.VolcanoVillage
        weatherRepo.refreshDashboard(v.id, v.latitude, v.longitude, forceRefresh = false)
        return Result.success()
    }
}
