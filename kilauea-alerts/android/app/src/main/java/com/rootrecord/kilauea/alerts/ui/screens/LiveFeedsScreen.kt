package com.rootrecord.kilauea.alerts.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.data.repository.LiveFeedsRepository
import com.rootrecord.kilauea.alerts.domain.LiveFeed
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.components.LiveFeedEmbedUrl
import com.rootrecord.kilauea.alerts.ui.components.YoutubeStreamEmbed
import com.rootrecord.kilauea.alerts.ui.livefeeds.LiveFeedsViewModel

@Composable
fun LiveFeedsScreen(vm: LiveFeedsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val newBadge by vm.newBadge.collectAsState()
    val ctx = LocalContext.current
    val feeds = when {
        state.catalog != null -> state.catalog!!.feeds
        state.loading -> emptyList()
        else -> LiveFeedsRepository.defaultStreams()
    }
    var selectedFeedIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.visitedTab()
    }

    LaunchedEffect(feeds.size) {
        if (selectedFeedIndex >= feeds.size && feeds.isNotEmpty()) {
            selectedFeedIndex = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DisclaimerBanner()
            if (newBadge) {
                Text(
                    "Stream list updated.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                "Use the tabs to switch streams. Video controls stay inside the player — links are updated from Root Record when USGS changes cameras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.loading && state.catalog == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (feeds.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No streams available. Pull refresh or try again later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val safeSelectedIndex = selectedFeedIndex.coerceIn(0, feeds.lastIndex)
                ScrollableTabRow(
                    selectedTabIndex = safeSelectedIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    feeds.forEachIndexed { index, feed ->
                        val short = feed.title
                            .removePrefix("[")
                            .substringBefore("]")
                            .ifBlank { feed.title }
                            .take(22)
                        Tab(
                            selected = safeSelectedIndex == index,
                            onClick = { selectedFeedIndex = index },
                            text = { Text(short, maxLines = 1) },
                        )
                    }
                }
                LiveFeedPage(
                    feed = feeds[safeSelectedIndex],
                    playVideo = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onOpenExternal = {
                        CustomTabsIntent.Builder().build()
                            .launchUrl(ctx, Uri.parse(feeds[safeSelectedIndex].watchUrl))
                    },
                )
            }
            if (feeds.isNotEmpty()) {
                TextButton(
                    onClick = {
                        CustomTabsIntent.Builder().build()
                            .launchUrl(ctx, Uri.parse("https://www.usgs.gov/volcanoes/kilauea/webcams"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Text("All USGS Kīlauea webcams (directory)", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        FloatingActionButton(
            onClick = { vm.refresh(true) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh streams")
        }
    }
}

@Composable
private fun LiveFeedPage(
    feed: LiveFeed,
    playVideo: Boolean,
    modifier: Modifier = Modifier,
    onOpenExternal: () -> Unit,
) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(feed.title, style = MaterialTheme.typography.titleMedium)
        if (feed.description.isNotBlank()) {
            Text(
                feed.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (playVideo) {
                YoutubeStreamEmbed(
                    loadUrl = LiveFeedEmbedUrl(feed),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        TextButton(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Text("Open in YouTube", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
