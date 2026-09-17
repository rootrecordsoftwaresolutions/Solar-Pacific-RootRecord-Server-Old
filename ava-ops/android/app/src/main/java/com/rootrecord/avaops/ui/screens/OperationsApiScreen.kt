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
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.OpsTransport
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaDanger
import com.rootrecord.avaops.ui.theme.AvaDivider
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill

data class ConnectionAttempt(val label: String, val detail: String, val time: String, val ok: Boolean, val timeout: Boolean = false)

@Composable
fun OperationsApiScreen(
    transport: OpsTransport,
    host: String,
    port: String,
    lastHandshake: String,
    attempts: List<ConnectionAttempt>,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val connected = transport != OpsTransport.DISCONNECTED
    DetailScaffold(title = "Operations API", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (connected) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (connected) AvaAccent else AvaWarning,
                        )
                        Text(
                            if (connected) "Connected" else "Disconnected",
                            color = if (connected) AvaAccent else AvaWarning,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        if (connected) "Using $host${if (port.isNotBlank()) ":$port" else ""}" else "Not connected to $host",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AvaTextMuted,
                    )
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry connection") }
                }
            }
            item {
                PanelCard {
                    Text("Host", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    OutlinedTextField(value = host, onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Port", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    OutlinedTextField(value = port, onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("API Key", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = "••••••••", onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Visibility, contentDescription = "Show key", tint = AvaTextMuted)
                    }
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Save & Test") }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AvaAccent)
                        Column {
                            Text("Last successful handshake", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                            Text(lastHandshake, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                PanelCard {
                    Text("Connection attempt log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    attempts.forEachIndexed { index, attempt ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (attempt.ok) Icons.Outlined.CheckCircle else if (attempt.timeout) Icons.Outlined.Warning else Icons.Outlined.Cancel,
                                    contentDescription = null,
                                    tint = if (attempt.ok) AvaAccent else if (attempt.timeout) AvaWarning else AvaDanger,
                                )
                                Column {
                                    Text(attempt.label, fontWeight = FontWeight.SemiBold, color = if (attempt.ok) AvaAccent else if (attempt.timeout) AvaWarning else AvaDanger)
                                    Text(attempt.detail, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                                }
                            }
                            Text(attempt.time, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        }
                        if (index < attempts.lastIndex) HorizontalDivider(color = AvaDivider)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
