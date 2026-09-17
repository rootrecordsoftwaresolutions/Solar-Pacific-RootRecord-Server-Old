package com.rootrecord.kilauea.alerts.ui.screens

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.upsell.UpsellEvents
import com.rootrecord.kilauea.alerts.ui.util.briefAirQualityLine
import com.rootrecord.kilauea.alerts.ui.util.briefWeatherSummary
import com.rootrecord.kilauea.alerts.ui.weather.WeatherViewModel

private const val FREE_TIER_LOC_ID = "volcano"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(
    navController: NavController,
    vm: WeatherViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val useMyLocation by vm.useMyLocationWeather.collectAsState()
    val proUnlocked by vm.proUnlocked.collectAsState()
    val ctx = LocalContext.current
    val fineLocation = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(proUnlocked, useMyLocation, fineLocation.status.isGranted) {
        if (proUnlocked && useMyLocation && fineLocation.status.isGranted) {
            val fused = LocationServices.getFusedLocationProviderClient(ctx)
            fused.lastLocation.addOnSuccessListener { loc ->
                loc?.let { vm.loadGpsWeather(it.latitude, it.longitude, false) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DisclaimerBanner(Modifier.padding(16.dp))
            Text(
                "Forecasts are saved on this phone. Each place shows when it was last refreshed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("My Location Weather", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (proUnlocked) {
                            "When turned on, uses your location for this forecast. Kept only on this phone."
                        } else {
                            "Pro: GPS-based forecast row and refresh. Free tier uses Volcano Village only."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useMyLocation && proUnlocked,
                    onCheckedChange = { on ->
                        if (!proUnlocked) {
                            UpsellEvents.trigger()
                        } else {
                            vm.setUseMyLocationWeather(on)
                            if (on && !fineLocation.status.isGranted) {
                                fineLocation.launchPermissionRequest()
                            }
                        }
                    },
                )
            }
            if (proUnlocked && useMyLocation && !fineLocation.status.isGranted) {
                Text(
                    "Turn on location permission to load weather for where you are.",
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { fineLocation.launchPermissionRequest() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.gpsWeather?.let { wx ->
                val gpsLocked = !proUnlocked
                Card(
                    Modifier
                        .padding(16.dp)
                        .clickable {
                            if (gpsLocked) {
                                UpsellEvents.trigger()
                            } else {
                                navController.navigate("weather_detail/gps")
                            }
                        },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (gpsLocked) "My location (GPS) — Pro" else "My location (GPS)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(briefWeatherSummary(wx), style = MaterialTheme.typography.bodySmall)
                        state.airByLocationId["gps"]?.let { air ->
                            briefAirQualityLine(air)?.let { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                items(state.rows, key = { it.first.id }) { (loc, json) ->
                    val locked = !proUnlocked && loc.id != FREE_TIER_LOC_ID
                    Card(
                        Modifier.clickable {
                            if (locked) {
                                UpsellEvents.trigger()
                            } else {
                                navController.navigate("weather_detail/${loc.id}")
                            }
                        },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (locked) "${loc.label} — Pro" else loc.label,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            json?.let { Text(briefWeatherSummary(it), style = MaterialTheme.typography.bodySmall) }
                            state.airByLocationId[loc.id]?.let { air ->
                                briefAirQualityLine(air)?.let { line ->
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { vm.loadAll(true) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }
}
