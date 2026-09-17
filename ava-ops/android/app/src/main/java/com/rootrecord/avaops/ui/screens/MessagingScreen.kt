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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootrecord.avaops.ops.InboxInfo
import com.rootrecord.avaops.ui.theme.AvaAccent
import com.rootrecord.avaops.ui.theme.AvaPanel
import com.rootrecord.avaops.ui.theme.AvaTextMuted
import com.rootrecord.avaops.ui.theme.AvaWarning
import com.rootrecord.avaops.ui.theme.DetailScaffold
import com.rootrecord.avaops.ui.theme.StatusPill
import com.rootrecord.avaops.ui.theme.WaitingForLive

@Composable
fun MessagingScreen(inbox: InboxInfo?, onBack: () -> Unit, onRefresh: () -> Unit = {}) {
    DetailScaffold(title = "Messaging Inboxes", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (inbox == null) {
                item { WaitingForLive("Inbox status is not in the current board snapshot.") }
            } else {
                item {
                    InboxRow(
                        name = "Discord bot token",
                        detail = if (inbox.discordTokenSet) "Token present on Ava host" else "Token not set",
                        ok = inbox.discordTokenSet,
                    )
                }
                item {
                    InboxRow(
                        name = "Report subscribers",
                        detail = "${inbox.reportSubscribers} subscribed",
                        ok = inbox.reportSubscribers >= 0,
                    )
                }
                item {
                    InboxRow(
                        name = "Report inbox file",
                        detail = if (inbox.inboxFile) "report-inbox.json present" else "No inbox file on disk",
                        ok = inbox.inboxFile,
                    )
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh messaging")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InboxRow(name: String, detail: String, ok: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Chat, contentDescription = null, tint = AvaTextMuted)
                Column {
                    Text(name, fontWeight = FontWeight.Bold)
                    Text(detail, color = AvaTextMuted)
                }
            }
            StatusPill(if (ok) "Ready" else "Missing", if (ok) AvaAccent else AvaWarning)
        }
    }
}
