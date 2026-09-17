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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.MinecraftLiveInfo
import com.rootrecord.avaops.ops.OpsServer
import com.rootrecord.avaops.ops.OriginInfo
import com.rootrecord.avaops.ops.ProcsInfo
import com.rootrecord.avaops.ops.ServerStatus
import com.rootrecord.avaops.ops.TunnelInfo
import com.rootrecord.avaops.ops.formatUptime
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill

@Composable
fun RootRecordServicesScreen(
    server: OpsServer,
    origin: OriginInfo?,
    procs: ProcsInfo?,
    tunnel: TunnelInfo?,
    minecraft: MinecraftLiveInfo?,
    rconCommand: String,
    onCommandChange: (String) -> Unit,
    onSend: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    val processRows = buildList {
        procs?.uvicornN?.let { add(LiveProcess("Ava origin (uvicorn)", "$it", "Board process count")) }
        procs?.electronN?.let { add(LiveProcess("Ava desk (electron)", "$it", "Board process count")) }
        tunnel?.processCount?.let { add(LiveProcess("cloudflared", "$it", tunnel.note ?: "Tunnel processes")) }
    }
    DetailScaffold(title = "RootRecord", subtitle = server.address ?: server.name, onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PanelCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(server.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    server.playersOnline != null && server.playerCapacity != null ->
                                        "${server.playersOnline}/${server.playerCapacity} players"
                                    server.playersOnline != null -> "${server.playersOnline} players"
                                    else -> "Player count unknown"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = AvaTextMuted,
                            )
                        }
                        StatusPill(
                            statusLabel(server.status),
                            if (server.status == ServerStatus.ONLINE) AvaAccent else AvaWarning,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PanelCard {
                        Text("Host uptime", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        Text(formatUptime(origin?.uptimeS), fontWeight = FontWeight.Bold, color = AvaAccent)
                    }
                    PanelCard {
                        Text("Jar / plugins", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        Text(minecraft?.jar ?: "—", fontWeight = FontWeight.Bold)
                        Text(minecraft?.plugins?.let { "$it plugins" } ?: "plugins unknown", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Start") }
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
                    OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("Restart") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ShowChart, contentDescription = null, tint = AvaTextMuted)
                    Text("Live processes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (processRows.isEmpty()) {
                item {
                    Text("No process counts in this snapshot.", color = AvaTextMuted)
                }
            }
            items(processRows, key = { it.name }) { process ->
                Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(process.name, fontWeight = FontWeight.Bold)
                            Text(process.detail, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        }
                        Text(process.count, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                PanelCard {
                    Text("RCON Quick Send", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Allowlisted command to ${server.id}", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rconCommand,
                            onValueChange = onCommandChange,
                            placeholder = { Text("Enter RCON command (e.g. list)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onSend, enabled = rconCommand.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Send")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class LiveProcess(val name: String, val count: String, val detail: String)

private fun statusLabel(status: ServerStatus): String = when (status) {
    ServerStatus.ONLINE -> "Online"
    ServerStatus.OFFLINE -> "Offline"
    ServerStatus.STARTING -> "Starting"
    ServerStatus.UNKNOWN -> "Unknown"
}
