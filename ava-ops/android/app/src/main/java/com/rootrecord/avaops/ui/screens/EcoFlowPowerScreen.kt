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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.SolarPower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.PowerDevice
import com.rootrecord.avaops.ops.PowerInfo
import com.rootrecord.avaops.ops.formatPct
import com.rootrecord.avaops.ops.formatWatts
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.PanelCard
import com.rootrecord.avaops.ui.theme.StatusPill
import com.rootrecord.avaops.ui.theme.WaitingForLive

@Composable
fun EcoFlowPowerScreen(power: PowerInfo?, onBack: () -> Unit, onRefresh: () -> Unit = {}) {
    val soc = power?.batteryPct
    val socColor = when {
        soc == null -> AvaTextMuted
        soc < 20 -> AvaWarning
        else -> AvaAccent
    }
    val onlineCount = power?.devices?.count { it.online } ?: 0
    DetailScaffold(title = "EcoFlow / Power", subtitle = power?.source ?: "Ava solar snapshot", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (power == null) {
                item { WaitingForLive("EcoFlow / solar is not in the current board snapshot.") }
            } else {
                item {
                    PanelCard {
                        Text("STATE OF CHARGE", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        Text(formatPct(soc), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = socColor)
                        Text(power.state ?: if (power.live) "Live EcoFlow" else "Cached / derived", color = AvaTextMuted)
                        if (power.detail != null) {
                            Text(power.detail, style = MaterialTheme.typography.labelSmall, color = AvaWarning)
                        }
                    }
                }
                item {
                    val genColor = when {
                        power.overCeiling == true -> AvaWarning
                        power.generator == true -> AvaAccent
                        else -> AvaTextMuted
                    }
                    PanelCard {
                        Text("GENERATOR", style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
                        Text(
                            when {
                                power.overCeiling == true -> "FAULT — over ceiling"
                                power.generator == true -> "ON"
                                power.bleGone == true -> "UNKNOWN"
                                else -> "OFF"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = genColor,
                        )
                        Text(
                            power.generatorCard
                                ?: "Facts from the desk live file. Not a Settings toggle.",
                            color = AvaTextMuted,
                        )
                        if (power.acInW != null) {
                            Text("AC-in ${formatWatts(power.acInW)}", color = AvaTextMuted)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PowerStat(Icons.Outlined.Bolt, "LOAD", formatWatts(power.loadW), Modifier.weight(1f))
                        PowerStat(Icons.Outlined.SolarPower, "SOLAR IN", formatWatts(power.solarInW), Modifier.weight(1f))
                    }
                }
                item {
                    PowerStat(Icons.Outlined.Bolt, "EBATT IN", formatWatts(power.ebattInW), Modifier.fillMaxWidth())
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Battery packs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        StatusPill("$onlineCount ONLINE", if (onlineCount > 0) AvaAccent else AvaWarning)
                    }
                }
                if (power.devices.isEmpty()) {
                    item {
                        Text("No pack rows in this snapshot.", color = AvaTextMuted)
                    }
                }
                items(power.devices.size) { index ->
                    PackRow(power.devices[index])
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh power")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PackRow(pack: PowerDevice) {
    Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(pack.label, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        pack.soc?.let { "$it%" },
                        pack.inputKind,
                        pack.pvW?.let { "PV ${formatWatts(it)}" },
                        pack.acOutW?.let { "AC ${formatWatts(it)}" },
                    ).joinToString(" · ").ifBlank { "No extra fields" },
                    style = MaterialTheme.typography.labelSmall,
                    color = AvaTextMuted,
                )
            }
            StatusPill(if (pack.online) "ONLINE" else "OFFLINE", if (pack.online) AvaAccent else AvaWarning)
        }
    }
}

@Composable
private fun PowerStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = AvaPanel), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = AvaAccent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
