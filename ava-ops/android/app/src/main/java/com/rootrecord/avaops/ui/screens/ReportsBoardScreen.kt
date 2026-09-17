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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.ReportWindow
import com.rootrecord.avaops.ops.ReportsInfo
import com.rootrecord.avaops.ops.formatEpochMs
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.InfoRow
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill
import com.rootrecord.avaops.ui.theme.WaitingForLive

@Composable
fun ReportsBoardScreen(reports: ReportsInfo?, onBack: () -> Unit, onRefresh: () -> Unit = {}) {
    DetailScaffold(title = "Reports Board", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (reports == null) {
                item { WaitingForLive("Reports board is not in the current snapshot.") }
            } else {
                item {
                    PanelCard {
                        Text("Current file", fontWeight = FontWeight.Bold)
                        InfoRow("Present", if (reports.currentExists) "Yes" else "No")
                        InfoRow("Name", reports.currentName ?: "—")
                        InfoRow("Written", formatEpochMs(reports.currentMtimeMs))
                        InfoRow("HST day", reports.hstDay ?: "—")
                        InfoRow(
                            "Metrics freshness",
                            when (reports.freshnessOk) {
                                true -> "Fresh"
                                false -> "Stale"
                                null -> "—"
                            },
                        )
                    }
                }
                item {
                    Text("Due today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (reports.dueToday.isEmpty()) {
                    item { Text("No due windows in this snapshot.", color = AvaTextMuted) }
                }
                items(reports.dueToday, key = { it.id.ifBlank { it.label } }) { window ->
                    ReportRow(window)
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh reports")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ReportRow(window: ReportWindow) {
    val ready = window.done || window.status.equals("done", ignoreCase = true)
    Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = if (ready) AvaAccent else AvaWarning,
                )
                Column {
                    Text(window.label.ifBlank { window.id }, fontWeight = FontWeight.Bold)
                    Text(window.whenLabel ?: window.status, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                }
            }
            StatusPill(if (ready) "Done" else window.status, if (ready) AvaAccent else AvaWarning)
        }
    }
}
