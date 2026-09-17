package com.rootrecord.kilauea.alerts.domain

import kotlinx.serialization.Serializable

@Serializable
data class LiveFeed(
    val id: String,
    val title: String,
    val description: String,
    /** When known — enables in-app YouTube embed. */
    val youtubeVideoId: String? = null,
    /** Primary browser / app fallback. */
    val watchUrl: String,
    /** Server-computed embed URL (iframe or channel /live page). */
    val embedUrl: String? = null,
    val thumbnailUrl: String? = null,
)
