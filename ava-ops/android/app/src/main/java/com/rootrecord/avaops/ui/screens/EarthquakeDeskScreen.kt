package com.rootrecord.avaops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.QuakeEvent
import com.rootrecord.avaops.ops.QuakesInfo
import com.rootrecord.avaops.ops.formatEpochMs
import com.rootrecord.avaops.ops.formatIso
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill
import com.rootrecord.avaops.ui.theme.WaitingForLive
import java.util.Locale

@Composable
fun EarthquakeDeskScreen(quakes: QuakesInfo?, onBack: () -> Unit, onRefresh: () -> Unit = {}) {
    val island = quakes?.island.orEmpty()
    val global = quakes?.global.orEmpty()
    DetailScaffold(title = "Earthquake Desk", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusPill(
                    if (quakes != null) "USGS live" else "No quake feed",
                    if (quakes != null) AvaAccent else AvaWarning,
                )
            }
            if (quakes == null) {
                item { WaitingForLive("USGS quake lists are not in the current board snapshot.") }
            } else {
                item {
                    PanelCard {
                        Text("Island events (24h window)", fontWeight = FontWeight.Bold)
                        Text("${island.size} events in snapshot", color = AvaTextMuted)
                        Text("Fetched ${formatIso(quakes.fetchedAt)}", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    }
                }
                item {
                    Text("Hawaiʻi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (island.isEmpty()) {
                    item {
                        Text("No island events in this snapshot.", style = MaterialTheme.typography.bodySmall, color = AvaTextMuted)
                    }
                }
                items(island, key = { it.id ?: "${it.timeMs}-${it.place}" }) { quake ->
                    QuakeRow(quake)
                }
                item {
                    Text("Global (M2.5+ day)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (global.isEmpty()) {
                    item {
                        Text("No global events in this snapshot.", style = MaterialTheme.typography.bodySmall, color = AvaTextMuted)
                    }
                }
                items(global, key = { it.id ?: "g-${it.timeMs}-${it.place}" }) { quake ->
                    QuakeRow(quake)
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh USGS")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QuakeRow(quake: QuakeEvent) {
    Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(quake.magnitude?.let { String.format(Locale.US, "M%.1f", it) } ?: "M—", AvaAccent)
            Column(Modifier.weight(1f)) {
                Text(quake.place ?: "Unknown place", style = MaterialTheme.typography.bodySmall)
                Text(formatEpochMs(quake.timeMs), style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
            }
            Text("sig ${quake.sig ?: "—"}", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
        }
    }
}
