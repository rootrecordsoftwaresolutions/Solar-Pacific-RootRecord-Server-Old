package com.rootrecord.kilauea.alerts.domain

import kotlinx.serialization.Serializable

@Serializable
data class LiveFeedsCatalog(
    val feeds: List<LiveFeed>,
    val syncedAtEpochMs: Long,
    val contentSha256: String,
)
