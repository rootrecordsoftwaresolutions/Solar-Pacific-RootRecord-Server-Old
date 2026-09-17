package com.rootrecord.avaops.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.AvaBoardSnapshot
import com.rootrecord.avaops.ops.OpsServer
import com.rootrecord.avaops.ops.OpsTransport
import com.rootrecord.avaops.ops.ServerStatus
import com.rootrecord.avaops.ops.fallbackQuickStatus
import com.rootrecord.avaops.ops.quickStatusTiles
import com.rootrecord.avaops.ui.data.QuickStatusItem
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaDivider
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill

@Composable
fun HomeScreen(
    transport: OpsTransport,
    transportDetail: String,
    servers: List<OpsServer>,
    selectedServerId: String,
    onSelectServer: (String) -> Unit,
    onServerAction: (String, String) -> Unit,
    rconCommand: String,
    onRconCommandChange: (String) -> Unit,
    onRconSend: () -> Unit,
    audit: List<String>,
    board: AvaBoardSnapshot?,
    boardError: String?,
    onOpenOperationsApi: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenQuickStatus: (String) -> Unit,
) {
    val tiles = board?.quickStatusTiles() ?: fallbackQuickStatus()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AvaPanel),
                modifier = Modifier.clickable(onClick = onOpenOperationsApi),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.padding(end = 12.dp)) {
                        Text("Operations API", style = MaterialTheme.typography.labelLarge)
                        Text(transportDetail, style = MaterialTheme.typography.bodySmall, color = AvaTextMuted)
                        if (boardError != null) {
                            Text(boardError, style = MaterialTheme.typography.labelSmall, color = AvaWarning)
                        } else if (board?.generatedAt != null) {
                            Text("Board ${board.generatedAt}", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        }
                    }
                    StatusPill(
                        if (transport == OpsTransport.DISCONNECTED) "Disconnected" else transport.homeLabel(),
                        if (transport == OpsTransport.DISCONNECTED) AvaWarning else AvaAccent,
                    )
                }
            }
        }
        item {
            Text("RootRecord Services", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (servers.isEmpty()) {
            item {
                PanelCard {
                    Text("No live servers yet", fontWeight = FontWeight.SemiBold)
                    Text("Refresh after Bluetooth or LAN connects to Ava.", style = MaterialTheme.typography.bodySmall, color = AvaTextMuted)
                }
            }
        }
        items(servers, key = { it.id }) { server ->
            Card(colors = CardDefaults.cardColors(containerColor = if (server.id == selectedServerId) Color(0xFF1C2A20) else AvaPanel)) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp).clickable {
                        onSelectServer(server.id)
                        onOpenServices()
                    },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(server.address ?: "RootRecord", style = MaterialTheme.typography.bodySmall, color = AvaTextMuted)
                        }
                        val stateColor = when (server.status) {
                            ServerStatus.ONLINE -> AvaAccent
                            ServerStatus.STARTING -> AvaWarning
                            else -> Color(0xFFFF7474)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(server.status.homeLabel(), color = stateColor, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    server.playersOnline != null && server.playerCapacity != null ->
                                        "${server.playersOnline}/${server.playerCapacity} players"
                                    server.playersOnline != null -> "${server.playersOnline} online"
                                    else -> "players unknown"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onServerAction(server.id, "start") }) { Text("Start") }
                    OutlinedButton(onClick = { onServerAction(server.id, "stop") }) { Text("Stop") }
                    Button(onClick = { onServerAction(server.id, "restart") }) { Text("Restart") }
                }
            }
        }
        item {
            val activeLabel = servers.firstOrNull { it.id == selectedServerId }?.name ?: selectedServerId.ifBlank { "none" }
            PanelCard {
                Text("Allowlisted RCON", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("RootRecord · $activeLabel", style = MaterialTheme.typography.bodySmall, color = AvaTextMuted)
                OutlinedTextField(
                    value = rconCommand,
                    onValueChange = onRconCommandChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Command") },
                    placeholder = { Text("list, say ..., save-all") },
                )
                Button(onClick = onRconSend, enabled = rconCommand.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Send command")
                }
            }
        }
        item {
            Text("Quick status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tiles, key = { it.id }) { item -> QuickStatusTile(item, onClick = { onOpenQuickStatus(item.id) }) }
            }
        }
        item {
            Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (audit.isEmpty()) {
                        Text("No activity yet", style = MaterialTheme.typography.bodyMedium, color = AvaTextMuted)
                    }
                    audit.forEachIndexed { index, entry ->
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                        if (index < audit.lastIndex) HorizontalDivider(color = AvaDivider)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuickStatusTile(item: QuickStatusItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AvaPanel),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
            StatusPill(item.status, if (item.healthy) AvaAccent else AvaWarning)
        }
    }
}

private fun OpsTransport.homeLabel(): String = when (this) {
    OpsTransport.BLUETOOTH -> "Bluetooth"
    OpsTransport.LOCALHOST -> "Local"
    OpsTransport.CLOUDFLARE -> "Cloudflare"
    OpsTransport.DISCONNECTED -> "Disconnected"
}

private fun ServerStatus.homeLabel(): String = when (this) {
    ServerStatus.ONLINE -> "Online"
    ServerStatus.OFFLINE -> "Offline"
    ServerStatus.STARTING -> "Starting"
    ServerStatus.UNKNOWN -> "Unknown"
}
