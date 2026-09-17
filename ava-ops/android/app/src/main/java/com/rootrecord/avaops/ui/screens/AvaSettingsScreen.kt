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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.FeatureFlagsSnapshot
import com.rootrecord.avaops.ops.OpsProvider
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.PanelCard
import kotlinx.coroutines.launch

private val TOGGLE_ROWS = listOf(
    Triple("obs", "OBS jobs", "Allow OBS WebSocket jobs only if you already opened OBS. Ava does not launch it."),
    Triple("radio_on_air", "Radio on air", "Public radio / sticky on-air. Off at boot until you turn it on."),
    Triple("music_bed", "Music bed", "Background music on this desk."),
    Triple("morning_report", "Morning report", "Timed morning spoken/text report."),
    Triple("midday_report", "Midday report", "Timed midday spoken/text report."),
    Triple("startup_voice", "Startup voice", "Play a clip when origin starts."),
    Triple("cloud_report_spend", "Cloud report spend", "Morning/midday/evening/late use cloud engine + mp3. Off means local."),
    Triple("companions_poller", "Companions poller", "Discord/Slack poller on this desk. Off at boot until you turn it on."),
    Triple("local_edge", "Local edge", "Local-edge gateway on this desk (:8791 if installed). Off at boot until you turn it on."),
)

@Composable
fun AvaSettingsScreen(onBack: () -> Unit, provider: OpsProvider) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<FeatureFlagsSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var idleMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            runCatching { provider.features() }
                .onSuccess {
                    snapshot = it
                    error = null
                }
                .onFailure {
                    error = it.message ?: "features unavailable"
                }
        }
    }

    LaunchedEffect(provider) { refresh() }

    fun setFlag(key: String, value: Boolean) {
        scope.launch {
            busy = true
            runCatching { provider.setFeatures(mapOf(key to value)) }
                .onSuccess {
                    snapshot = it
                    error = null
                }
                .onFailure {
                    error = it.message ?: "toggle failed"
                    refresh()
                }
            busy = false
        }
    }

    DetailScaffold(title = "Settings", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PanelCard {
                    Text("Optional stacks", fontWeight = FontWeight.Bold)
                    Text(
                        "These stay off at login until you flip them here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AvaTextMuted,
                    )
                    val obsOpen = snapshot?.obsProcessRunning == true
                    Text(
                        if (obsOpen) "OBS is open on the desk." else "OBS is not running.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AvaTextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (error != null) {
                item {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
            TOGGLE_ROWS.forEach { (key, title, hint) ->
                item {
                    PanelCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
                                Text(title, fontWeight = FontWeight.Bold)
                                Text(hint, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                            }
                            Switch(
                                checked = snapshot?.flags?.get(key) == true,
                                enabled = !busy && snapshot != null,
                                onCheckedChange = { setFlag(key, it) },
                            )
                        }
                    }
                }
            }
            item {
                PanelCard {
                    Text("Idle desk", fontWeight = FontWeight.Bold)
                    Text(
                        "Stops origin, Ollama, Bluetooth bridge, OBS, music, and companions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AvaTextMuted,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                runCatching { provider.idleStop() }
                                    .onSuccess { idleMessage = "Idle stop started. This desk will go quiet." }
                                    .onFailure { idleMessage = it.message ?: "idle stop failed" }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Idle desk")
                    }
                    if (idleMessage != null) {
                        Text(
                            idleMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = AvaTextMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
