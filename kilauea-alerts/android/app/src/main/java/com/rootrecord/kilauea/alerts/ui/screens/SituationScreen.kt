package com.rootrecord.kilauea.alerts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.situation.SituationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SituationScreen(
    onBack: () -> Unit,
    vm: SituationViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val situation = state.situation

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        situation?.name?.takeIf { it.isNotBlank() } ?: "Major event",
                        maxLines = 2,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.load(force = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh event page")
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerBanner()
            if (state.loading && situation == null) {
                CircularProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
            if (!state.loading && situation == null && state.error == null) {
                Text(
                    "No active major-event page is published right now.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            situation?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            s.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (s.body.isNotBlank()) {
                            Text(
                                s.body,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            Text(
                                "Details will be posted here. Follow official USGS/HVO sources for authoritative updates.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.updatedAt.isNotBlank()) {
                            Text(
                                "Last updated (server): ${s.updatedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "This page is published remotely for major Kīlauea events (for example lower rift activity or lava reaching the ocean). It supplements — does not replace — official USGS/HVO statements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
