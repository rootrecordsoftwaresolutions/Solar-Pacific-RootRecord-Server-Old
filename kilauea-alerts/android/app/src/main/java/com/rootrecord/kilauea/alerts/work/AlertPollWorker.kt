package com.rootrecord.kilauea.alerts.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.EarthquakeRepository
import com.rootrecord.kilauea.alerts.data.repository.NwsAlertsRepository
import com.rootrecord.kilauea.alerts.data.repository.UsgVolcanoRepository
import com.rootrecord.kilauea.alerts.notifications.AlertDiffer
import com.rootrecord.kilauea.alerts.notifications.KilaueaNotificationManager
import com.rootrecord.kilauea.alerts.notifications.VolcanoNoticeSignals
import com.rootrecord.kilauea.alerts.util.isAppInForeground
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AlertPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val volcanoRepo: UsgVolcanoRepository,
    private val nwsRepo: NwsAlertsRepository,
    private val eqRepo: EarthquakeRepository,
    private val prefs: KilaueaPreferences,
    private val notifications: KilaueaNotificationManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notifications.ensureChannels()
        val pro = prefs.authProUnlocked.first()
        val volcanoOn = prefs.notificationVolcano.first()
        val volcanoElevatedOnly = prefs.notificationVolcanoElevatedOnly.first()
        val nwsOn = pro && prefs.notificationNws.first()
        val eqOn = pro && prefs.notificationEq.first()
        val threshold = prefs.eqMagnitudeThreshold.first()
        val bootstrapped = prefs.bootstrapNotifyComplete.first()
        // When the app is open we refresh in the UI; showing a tray alert here feels like "limbo" delivery.
        val showTrayAlerts = !isAppInForeground()

        val volJson = volcanoRepo.refreshVolcanoStatus().getOrNull()
        val nwsJson = nwsRepo.refresh().getOrNull()
        val eqJson = eqRepo.refreshEarthquakes().getOrNull()

        if (!bootstrapped) {
            val eqMinTime = System.currentTimeMillis() - AlertDiffer.EQ_ALERT_RECENCY_MS
            volJson?.let {
                val id = AlertDiffer.volcanoNewestId(it)
                if (id != null) {
                    prefs.setNotifiedVolcanoIds(AlertDiffer.encodeStringSet(setOf(id)))
                }
            }
            nwsJson?.let {
                prefs.setNotifiedNwsIds(AlertDiffer.encodeStringSet(AlertDiffer.nwsFeatureIds(it)))
            }
            eqJson?.let {
                prefs.setNotifiedEqIds(
                    AlertDiffer.encodeStringSet(
                        AlertDiffer.earthquakeEventIds(it, threshold.toDouble(), eqMinTime),
                    ),
                )
            }
            prefs.setEqNotifiedAtThreshold(threshold)
            prefs.setBootstrapNotifyComplete(true)
            return Result.success()
        }

        if (volcanoOn && volJson != null) {
            val curId = AlertDiffer.volcanoNewestId(volJson)
            val seen = AlertDiffer.parseStringSet(prefs.getNotifiedVolcanoIds())
            if (curId != null && curId !in seen) {
                val plain = VolcanoNoticeSignals.newestNoticePlainText(volJson)
                val urgent = VolcanoNoticeSignals.shouldUseUrgentVolcanoNotification(volJson, plain)
                if (volcanoElevatedOnly && !urgent) {
                    prefs.setNotifiedVolcanoIds(AlertDiffer.encodeStringSet(seen + curId))
                } else if (urgent) {
                    if (showTrayAlerts) {
                        notifications.notifyVolcano(
                            "Kīlauea — eruptive activity or elevated unrest (USGS)",
                            "A new USGS notice describes eruption, lava, strong unrest, or elevated aviation color. Open Alerts for the official wording.",
                            urgent = true,
                        )
                    }
                    prefs.setNotifiedVolcanoIds(AlertDiffer.encodeStringSet(seen + curId))
                } else {
                    if (showTrayAlerts) {
                        notifications.notifyVolcano(
                            "Kīlauea update (USGS)",
                            "New volcano notice or status change. Open the app for official details.",
                            urgent = false,
                        )
                    }
                    prefs.setNotifiedVolcanoIds(AlertDiffer.encodeStringSet(seen + curId))
                }
            }
        }

        if (nwsOn && nwsJson != null) {
            val cur = AlertDiffer.nwsFeatureIds(nwsJson)
            val seen = AlertDiffer.parseStringSet(prefs.getNotifiedNwsIds())
            val newIds = AlertDiffer.newIds(cur, seen)
            if (newIds.isNotEmpty()) {
                if (showTrayAlerts) {
                    val summary =
                        if (newIds.size == 1) "New NWS alert for Hawaiʻi." else "${newIds.size} new NWS alerts for Hawaiʻi."
                    notifications.notifyNws("Weather alert (NWS)", summary)
                }
                prefs.setNotifiedNwsIds(AlertDiffer.encodeStringSet(seen + newIds))
            }
        }

        if (eqOn && eqJson != null) {
            val now = System.currentTimeMillis()
            val minOriginTime = now - AlertDiffer.EQ_ALERT_RECENCY_MS
            val magThreshold = threshold.toDouble()
            val snapshot = prefs.getEqNotifiedAtThreshold()
            if (snapshot == null) {
                // Upgrade from builds without threshold snapshot: align IDs to stable scheme + recency (no notify).
                val cur = AlertDiffer.earthquakeEventIds(eqJson, magThreshold, minOriginTime)
                prefs.setNotifiedEqIds(AlertDiffer.encodeStringSet(cur))
                prefs.setEqNotifiedAtThreshold(threshold)
            } else if (snapshot != threshold) {
                val cur = AlertDiffer.earthquakeEventIds(eqJson, magThreshold, minOriginTime)
                prefs.setNotifiedEqIds(AlertDiffer.encodeStringSet(cur))
                prefs.setEqNotifiedAtThreshold(threshold)
            } else {
                val cur = AlertDiffer.earthquakeEventIds(eqJson, magThreshold, minOriginTime)
                val seen = AlertDiffer.parseStringSet(prefs.getNotifiedEqIds())
                val newIds = AlertDiffer.newIds(cur, seen)
                if (newIds.isNotEmpty()) {
                    if (showTrayAlerts) {
                        val primaryId = AlertDiffer.newestEarthquakeIdAmong(eqJson, newIds)
                            ?: newIds.first()
                        val detail = AlertDiffer.earthquakeSummaryForEventId(eqJson, primaryId)
                        val body = detail?.let { "$it (≥ M ${"%.1f".format(threshold)}, USGS Hawaiʻi region)." }
                            ?: "Magnitude ≥ ${"%.1f".format(threshold)} in Hawaiʻi region (USGS)."
                        notifications.notifyEarthquake(
                            "Earthquake (USGS)",
                            body,
                        )
                    }
                    prefs.setNotifiedEqIds(AlertDiffer.encodeStringSet(seen + newIds))
                }
            }
        }

        return Result.success()
    }
}
