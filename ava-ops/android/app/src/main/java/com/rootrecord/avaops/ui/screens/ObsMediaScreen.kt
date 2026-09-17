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
import androidx.compose.material.icons.outlined.Folder
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
import com.rootrecord.avaops.ops.MediaFolderInfo
import com.rootrecord.avaops.ops.MediaInfo
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
fun ObsMediaScreen(media: MediaInfo?, onBack: () -> Unit, onRefresh: () -> Unit = {}) {
    DetailScaffold(title = "OBS & Media", onBack = onBack) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (media == null) {
                item { WaitingForLive("Media folders are not in the current board snapshot.") }
            } else {
                item { FolderCard("Public media", media.public) }
                item { FolderCard("Private media", media.private) }
                val types = (media.public?.typeNames.orEmpty() + media.private?.typeNames.orEmpty()).distinct()
                if (types.isNotEmpty()) {
                    item {
                        Text("Types on disk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(types, key = { it }) { name ->
                        Card(colors = CardDefaults.cardColors(containerColor = AvaPanel)) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = AvaTextMuted)
                                Text(name, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh media")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FolderCard(title: String, folder: MediaFolderInfo?) {
    PanelCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            StatusPill(
                if (folder?.exists == true) "On disk" else "Missing",
                if (folder?.exists == true) AvaAccent else AvaWarning,
            )
        }
        InfoRow("Path", folder?.path ?: "—")
        InfoRow("Type folders", folder?.typeCount?.toString() ?: "—")
        if (!folder?.typeNames.isNullOrEmpty()) {
            Text(folder.typeNames.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = AvaTextMuted)
        }
    }
}
