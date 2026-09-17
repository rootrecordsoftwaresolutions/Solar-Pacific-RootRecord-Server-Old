package com.rootrecord.kilauea.alerts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.domain.BigIslandLocation
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.weather.WeatherDetailViewModel
import com.rootrecord.kilauea.alerts.ui.weather.WeatherManagerDetailContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    locationId: String,
    onBack: () -> Unit,
    vm: WeatherDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(locationId) {
        vm.load(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (locationId) {
                            WeatherDetailViewModel.WEATHER_GPS_ROUTE_ID -> "My location (GPS)"
                            else -> BigIslandLocation.byId(locationId)?.label ?: locationId
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DisclaimerBanner()
            Text(
                "Full detail: current conditions, hourly trend, weather alerts, and multi-day forecast.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.error != null ->
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                state.bundle != null ->
                    WeatherManagerDetailContent(
                        bundle = state.bundle!!,
                        airQuality = state.airQuality,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                else ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
            }
        }
    }
}
