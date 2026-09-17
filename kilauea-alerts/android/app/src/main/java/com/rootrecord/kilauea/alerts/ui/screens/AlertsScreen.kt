package com.rootrecord.kilauea.alerts.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.rootrecord.kilauea.alerts.ui.KilaueaNavRoutes
import com.rootrecord.kilauea.alerts.ui.alerts.AlertsViewModel
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.util.formatUsgsNewestForAlerts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val USGS_CARD_EXCERPT_CHARS = 900

@Composable
fun AlertsScreen(
    navController: NavController,
    vm: AlertsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val usgsBody = formatUsgsNewestForAlerts(state.volcano?.get("newest"))
    val feats = state.nws?.get("features")?.jsonArray ?: JsonArray(emptyList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DisclaimerBanner() }
            item {
                AlertsInfoCard(
                    body = "Volcano notices from the USGS and weather alerts for Hawaiʻi from the National Weather Service. Tap a card when you see a link.",
                )
            }
            item {
                AiAnalysisEntryCard(
                    onOpen = { navController.navigate(KilaueaNavRoutes.AiAnalysis) },
                )
            }
            item {
                VolcanoNoticeCard(
                    body = usgsBody,
                    onOpenUsgs = {
                        ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.usgs.gov/volcanoes/kilauea"),
                            ),
                        )
                    },
                )
            }
            item {
                Text(
                    "Hawaiʻi Weather Alerts (NWS)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
            if (feats.isEmpty()) {
                item {
                    AlertsInfoCard(
                        body = "No active weather alerts for Hawaiʻi right now.",
                        muted = true,
                    )
                }
            }
            items(feats.size) { idx ->
                val f = feats[idx].jsonObject
                val props = f["properties"]?.jsonObject ?: JsonObject(emptyMap())
                val headline = props["headline"]?.jsonPrimitive?.content
                    ?: props["event"]?.jsonPrimitive?.content
                val detail = props["description"]?.jsonPrimitive?.content
                val uri = props["uri"]?.jsonPrimitive?.content
                NwsAlertCard(
                    headline = headline ?: "Alert",
                    detail = detail,
                    onClick = uri?.let { u ->
                        {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        }
                    },
                )
            }
        }
        FloatingActionButton(
            onClick = { vm.load(true) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun AiAnalysisEntryCard(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("AI Analysis", style = MaterialTheme.typography.titleMedium)
            Text(
                "Compare qualifying USGS, NWS, earthquake, and tsunami triggers with the previous AI report.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("See Latest AI Analysis")
            }
        }
    }
}

@Composable
private fun AlertsInfoCard(body: String, muted: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = body,
            style = alertBodyStyle(),
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun VolcanoNoticeCard(body: String, onOpenUsgs: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Latest Volcano Notice (USGS)", style = MaterialTheme.typography.titleMedium)
            val excerpt =
                if (body.length > USGS_CARD_EXCERPT_CHARS) {
                    body.take(USGS_CARD_EXCERPT_CHARS).trimEnd() + "…"
                } else {
                    body
                }
            Text(
                text = excerpt,
                style = alertBodyStyle(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (body.length > USGS_CARD_EXCERPT_CHARS) {
                    "Read full notice on USGS →"
                } else {
                    "Full report on USGS →"
                },
                modifier = Modifier.clickable(onClick = onOpenUsgs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NwsAlertCard(headline: String, detail: String?, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(headline, style = MaterialTheme.typography.titleSmall)
            detail?.let {
                Text(
                    text = it.take(500).trim(),
                    style = alertBodyStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (it.length > 500 && onClick != null) {
                    Text(
                        "Tap for full alert on NWS →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Comfortable reading size inside cards (not full-screen wall of oversized type). */
@Composable
private fun alertBodyStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return base.copy(lineHeight = 24.sp)
}
