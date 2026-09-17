package com.rootrecord.kilauea.alerts.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.earthquakes.EarthquakesViewModel

/** Earthquake list from the USGS; tapping a row opens the official event page. */
@Composable
fun EarthquakesScreen(vm: EarthquakesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // Prefer saved data first so a fresh notification does not pair with a misleading “no updates” snackbar.
        vm.load(forceRefresh = false, showSnackbarIfUnchanged = false)
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeSnackbarMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.load(forceRefresh = true, showSnackbarIfUnchanged = true) },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            DisclaimerBanner(Modifier.padding(16.dp))
            Text(
                "Earthquakes near Hawaiʻi from the USGS, newest first. Magnitude and depth come from USGS.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows) { row ->
                    Card(onClick = {
                        row.url?.let { u ->
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        }
                    }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "M ${row.mag ?: "?"} · ${row.place ?: ""}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                row.timeMs?.let {
                                    java.text.SimpleDateFormat.getDateTimeInstance()
                                        .format(java.util.Date(it))
                                } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
