package com.rootrecord.kilauea.alerts.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.data.repository.AiAnalysisReport
import com.rootrecord.kilauea.alerts.ui.ai.AiAnalysisViewModel
import com.rootrecord.kilauea.alerts.ui.upsell.UpsellEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(
    onBack: () -> Unit,
    vm: AiAnalysisViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.load() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh AI Analysis")
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AiInfoCard()
            }
            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.loading && state.catalog == null) {
                item { CircularProgressIndicator() }
            }
            val reports = state.catalog?.reports.orEmpty()
            if (!state.loading && reports.isEmpty()) {
                item {
                    Text(
                        "No AI reports yet. Reports appear after qualifying USGS, NWS, earthquake, or tsunami triggers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(reports, key = { it.id }) { report ->
                AiReportCard(report)
            }
        }
    }
}

@Composable
private fun AiInfoCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI-assisted summary", style = MaterialTheme.typography.titleMedium)
            Text(
                "Reports are separated from official alerts and are not official USGS, HVO, or NWS releases. Always use official agency guidance as authoritative.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiReportCard(report: AiAnalysisReport) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(report.headline.ifBlank { sourceLabel(report.sourceType) }, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(
                    sourceLabel(report.sourceType),
                    report.event,
                    report.severity,
                    report.magnitude?.let { "M ${"%.1f".format(it)}" },
                    report.createdAt.takeIf { it.isNotBlank() },
                ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.previousSummary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(report.freeText, style = MaterialTheme.typography.bodyMedium)
            if (!report.proText.isNullOrBlank()) {
                Text("Member continuation", style = MaterialTheme.typography.titleSmall)
                Text(report.proText, style = MaterialTheme.typography.bodyMedium)
            } else if (report.proLocked) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("More analysis requires additional resources.", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "The public preview is shown above. Some analysis is limited to members only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { UpsellEvents.trigger() }, modifier = Modifier.fillMaxWidth()) {
                            Text("See plans")
                        }
                    }
                }
            }
            if (report.url.isNotBlank()) {
                TextButton(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.url))) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open official source")
                }
            }
        }
    }
}

private fun sourceLabel(sourceType: String): String =
    when (sourceType) {
        "volcano" -> "Volcano"
        "nws" -> "Weather Alert"
        "earthquake" -> "Earthquake"
        "tsunami" -> "Tsunami Context"
        else -> sourceType.replaceFirstChar { it.uppercase() }
    }
