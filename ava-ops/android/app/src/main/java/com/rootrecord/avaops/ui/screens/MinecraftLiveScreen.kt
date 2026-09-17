package com.rootrecord.avaops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.MinecraftLiveInfo
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.InfoRow
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill
import com.rootrecord.avaops.ui.theme.WaitingForLive

@Composable
fun MinecraftLiveScreen(minecraft: MinecraftLiveInfo?, onBack: () -> Unit, onRefresh: () -> Unit = {}) {
    DetailScaffold(title = "Minecraft Live", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (minecraft == null) {
                item { WaitingForLive("Minecraft status is not in the current board snapshot.") }
            } else {
                item {
                    PanelCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = AvaTextMuted)
                                Text("play.rootmc.net", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            StatusPill(if (minecraft.liveOnline) "Online" else "Down", if (minecraft.liveOnline) AvaAccent else AvaWarning)
                        }
                        InfoRow("Host", minecraft.liveHost ?: "—")
                        InfoRow("Port", minecraft.livePort?.toString() ?: "—")
                        InfoRow("Latency", minecraft.liveLatencyMs?.let { "${it} ms" } ?: "—")
                    }
                }
                item {
                    PanelCard {
                        Text("Test / local jar", fontWeight = FontWeight.Bold)
                        InfoRow("Test online", if (minecraft.testOnline) "Yes" else "No")
                        InfoRow("Test latency", minecraft.testLatencyMs?.let { "${it} ms" } ?: "—")
                        InfoRow("Jar", minecraft.jar ?: "—")
                        InfoRow("Plugins", minecraft.plugins?.toString() ?: "—")
                        InfoRow("Server dir", if (minecraft.dirPresent) "Present" else "Missing")
                    }
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Minecraft")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
