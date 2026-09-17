package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.local.KilaueaDataDao
import com.rootrecord.kilauea.alerts.data.local.KilaueaDataEntity
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import com.rootrecord.kilauea.alerts.data.remote.RootRecordApi
import com.rootrecord.kilauea.alerts.domain.LiveFeed
import com.rootrecord.kilauea.alerts.domain.LiveFeedsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveFeedsRepository @Inject constructor(
    private val api: RootRecordApi,
    private val dao: KilaueaDataDao,
    private val prefs: KilaueaPreferences,
) {

    suspend fun cachedCatalog(): LiveFeedsCatalog? {
        val raw = dao.getByKey(CacheKeys.LIVE_FEEDS_MERGED)?.payloadJson ?: return null
        val cat = runCatching { AppJson.decodeFromString(LiveFeedsCatalog.serializer(), raw) }.getOrNull()
            ?: return null
        // Pre–D1 builds cached USGS portal links; ignore so we refetch YouTube embed URLs.
        if (isLegacyPortalCatalog(cat)) return null
        // Drop catalogs that still include removed Lava Watchers feed.
        if (cat.feeds.any { it.id == REMOVED_LAVA_WATCHERS_ID }) return null
        return cat
    }

    suspend fun refreshMergedCatalog(): Result<LiveFeedsCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            val feeds = fetchRemoteStreams().getOrElse { defaultStreams() }
                .filterNot { it.id == REMOVED_LAVA_WATCHERS_ID }
            val json = AppJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(LiveFeed.serializer()),
                feeds,
            )
            val sha = sha256Hex(json)
            val merged = LiveFeedsCatalog(
                feeds = feeds,
                syncedAtEpochMs = System.currentTimeMillis(),
                contentSha256 = sha,
            )
            val prev = cachedCatalog()
            dao.upsert(
                KilaueaDataEntity(
                    cacheKey = CacheKeys.LIVE_FEEDS_MERGED,
                    payloadJson = AppJson.encodeToString(LiveFeedsCatalog.serializer(), merged),
                    fetchedAtEpochMs = merged.syncedAtEpochMs,
                    sourceUrl = "https://api.rootrecord.online/api/mobile/kilauea-live-streams",
                ),
            )
            if (prev != null && merged.feeds.any { f -> prev.feeds.none { it.id == f.id } }) {
                prefs.setLiveFeedsNewBadge(true)
            }
            merged
        }
    }

    suspend fun offlineFirst(force: Boolean): Result<LiveFeedsCatalog> {
        if (!force) {
            cachedCatalog()?.let { return Result.success(it) }
        }
        return refreshMergedCatalog()
    }

    suspend fun clearNewBadge() {
        prefs.setLiveFeedsNewBadge(false)
    }

    private suspend fun fetchRemoteStreams(): Result<List<LiveFeed>> = runCatching {
        val raw = api.kilaueaLiveStreams()
        val root = AppJson.parseToJsonElement(raw).jsonObject
        val arr = root["streams"]?.jsonArray ?: return@runCatching emptyList()
        arr.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (id == REMOVED_LAVA_WATCHERS_ID) return@mapNotNull null
            val watchUrl = o["watch_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            LiveFeed(
                id = id,
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { id },
                description = o["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                youtubeVideoId = o["youtube_video_id"]?.jsonPrimitive?.contentOrNull,
                watchUrl = watchUrl,
                embedUrl = o["embed_url"]?.jsonPrimitive?.contentOrNull,
                thumbnailUrl = null,
            )
        }
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isLegacyPortalCatalog(catalog: LiveFeedsCatalog): Boolean =
        catalog.feeds.any { feed ->
            feed.watchUrl.contains("usgs.gov", ignoreCase = true) &&
                (feed.id.startsWith("usgs_") || feed.id.startsWith("v") || feed.id.startsWith("yt_"))
        }

    companion object {
        private const val REMOVED_LAVA_WATCHERS_ID = "lava_watchers"

        /** Offline fallback — USGS official cams only (Lava Watchers removed). */
        fun defaultStreams(): List<LiveFeed> = listOf(
            LiveFeed(
                id = "usgs_v1",
                title = "[V1cam] West Halemaʻumaʻu",
                description = "USGS summit thermal/visual feed (official YouTube stream).",
                youtubeVideoId = "HggWKlZv9yk",
                watchUrl = "https://www.youtube.com/watch?v=HggWKlZv9yk",
                embedUrl = "https://www.youtube.com/embed/HggWKlZv9yk?autoplay=1&playsinline=1&rel=0&modestbranding=1",
            ),
            LiveFeed(
                id = "usgs_v2",
                title = "[V2cam] North Halemaʻumaʻu",
                description = "USGS summit area monitoring (official YouTube stream).",
                youtubeVideoId = "Tz5tPqRRv1Y",
                watchUrl = "https://www.youtube.com/watch?v=Tz5tPqRRv1Y",
                embedUrl = "https://www.youtube.com/embed/Tz5tPqRRv1Y?autoplay=1&playsinline=1&rel=0&modestbranding=1",
            ),
            LiveFeed(
                id = "usgs_v3",
                title = "[V3cam] Halemaʻumaʻu lava lake",
                description = "USGS lava lake view when active (official YouTube stream).",
                youtubeVideoId = "gXKuUyKt8mc",
                watchUrl = "https://www.youtube.com/watch?v=gXKuUyKt8mc",
                embedUrl = "https://www.youtube.com/embed/gXKuUyKt8mc?autoplay=1&playsinline=1&rel=0&modestbranding=1",
            ),
        )
    }
}
