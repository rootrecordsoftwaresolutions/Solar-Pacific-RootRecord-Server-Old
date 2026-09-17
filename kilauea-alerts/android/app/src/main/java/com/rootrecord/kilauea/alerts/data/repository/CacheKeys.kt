package com.rootrecord.kilauea.alerts.data.repository

object CacheKeys {
    const val VOLCANO_KILAUEA = "volcano_kilauea"
    const val EARTHQUAKES_FDSN = "earthquakes_fdsn"
    const val NWS_ALERTS_HI = "nws_alerts_hi"
    const val LIVE_FEEDS_MERGED = "live_feeds_merged"
    const val USGS_MESSAGES_RECENT = "usgs_messages_recent"
    const val SITUATION = "kilauea_situation"
    fun dashboard(locationId: String) = "dashboard:$locationId"

    fun airQuality(locationId: String) = "air_quality:$locationId"
}
