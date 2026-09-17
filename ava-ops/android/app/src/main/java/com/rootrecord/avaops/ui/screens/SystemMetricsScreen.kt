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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.HostInfo
import com.rootrecord.avaops.ops.MysqlInfo
import com.rootrecord.avaops.ops.OllamaInfo
import com.rootrecord.avaops.ops.OriginInfo
import com.rootrecord.avaops.ops.TunnelInfo
import com.rootrecord.avaops.ops.formatPct
import com.rootrecord.avaops.ops.formatUptime
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
fun SystemMetricsScreen(
    host: HostInfo?,
    mysql: MysqlInfo?,
    origin: OriginInfo? = null,
    tunnel: TunnelInfo? = null,
    ollama: OllamaInfo? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    DetailScaffold(title = "System Metrics", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (host == null && mysql == null && origin == null) {
                item { WaitingForLive("Host metrics are not in the current board snapshot.") }
            }
            if (host != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MetricTile("CPU", formatPct(host.cpuPct), Modifier.weight(1f))
                        MetricTile("RAM", formatPct(host.memPct), Modifier.weight(1f))
                    }
                }
                item {
                    PanelCard {
                        Text("Ava host", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        InfoRow("Memory", listOfNotNull(host.memUsedGb?.let { "$it GB used" }, host.memTotalGb?.let { "$it GB total" }).joinToString(" / ").ifBlank { "—" })
                        InfoRow("Temp", host.tempC?.let { "$it °C" } ?: "—")
                        InfoRow("Disk", formatPct(host.diskPct))
                        InfoRow("GPU", host.gpuName ?: "—")
                        InfoRow(
                            "Host battery",
                            buildString {
                                append(formatPct(host.hostBatteryPct))
                                when (host.hostBatteryPlugged) {
                                    true -> append(" · plugged")
                                    false -> append(" · unplugged")
                                    null -> Unit
                                }
                            },
                        )
                    }
                }
            }
            if (origin != null) {
                item {
                    PanelCard {
                        Text("Origin uptime", fontWeight = FontWeight.Bold)
                        InfoRow("Host uptime", formatUptime(origin.uptimeS))
                        InfoRow("Desk uptime", formatUptime(origin.deskUptimeS))
                        InfoRow("Last return", origin.lastReturnAt ?: "—")
                    }
                }
            }
            if (mysql != null) {
                item {
                    PanelCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MySQL live", fontWeight = FontWeight.Bold)
                            StatusPill(if (mysql.live) "Live" else "Down", if (mysql.live) AvaAccent else AvaWarning)
                        }
                        InfoRow("Local 3306", if (mysql.local3306) "Open" else "Closed")
                        InfoRow("Shockbyte", if (mysql.shockbyte) "Reachable" else "No")
                    }
                }
            }
            if (tunnel != null) {
                item {
                    PanelCard {
                        Text("Cloudflare tunnel", fontWeight = FontWeight.Bold)
                        InfoRow("Processes", tunnel.processCount?.toString() ?: "—")
                        InfoRow("Metrics", if (tunnel.metricsOk) "Answered" else "No")
                        InfoRow("Connections", tunnel.connections?.toString() ?: "—")
                        Text(tunnel.note ?: "", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    }
                }
            }
            if (ollama != null) {
                item {
                    PanelCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ollama", fontWeight = FontWeight.Bold)
                            StatusPill(if (ollama.up) "Up" else "Down", if (ollama.up) AvaAccent else AvaWarning)
                        }
                        Text(
                            if (ollama.models.isEmpty()) "No models listed" else ollama.models.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = AvaTextMuted,
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh metrics")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = AvaPanel), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AvaAccent)
        }
    }
}
