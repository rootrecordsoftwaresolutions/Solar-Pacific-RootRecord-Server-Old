package com.rootrecord.kilauea.alerts.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.AirQualityRepository
import com.rootrecord.kilauea.alerts.data.repository.WeatherRepository
import com.rootrecord.kilauea.alerts.domain.BigIslandLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

private const val FREE_TIER_LOC_ID = "volcano"

data class WeatherListUiState(
    val rows: List<Pair<BigIslandLocation, JsonObject?>> = emptyList(),
    val gpsWeather: JsonObject? = null,
    val airByLocationId: Map<String, JsonObject> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repo: WeatherRepository,
    private val airQualityRepo: AirQualityRepository,
    private val prefs: KilaueaPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherListUiState())
    val state = _state.asStateFlow()

    val useMyLocationWeather =
        prefs.weatherUseMyLocation.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Pro / Lifetime: server's `pro_unlocked` already OR's `life_member` in. */
    val proUnlocked =
        prefs.authProUnlocked.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var lastGpsLat: Double? = null
    private var lastGpsLon: Double? = null

    init {
        viewModelScope.launch {
            prefs.authProUnlocked.distinctUntilChanged().collect {
                loadAllInternal(force = false)
            }
        }
    }

    fun setUseMyLocationWeather(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !prefs.authProUnlocked.first()) return@launch
            prefs.setWeatherUseMyLocation(enabled)
            if (!enabled) {
                lastGpsLat = null
                lastGpsLon = null
                _state.update { it.copy(gpsWeather = null) }
            }
            loadAllInternal(force = false)
        }
    }

    fun loadAll(force: Boolean) {
        viewModelScope.launch { loadAllInternal(force) }
    }

    private suspend fun loadAllInternal(force: Boolean) {
        val pro = prefs.authProUnlocked.first()
        if (!pro) {
            if (prefs.weatherUseMyLocation.first()) {
                prefs.setWeatherUseMyLocation(false)
            }
            lastGpsLat = null
            lastGpsLon = null
        }

        _state.update { it.copy(loading = true, error = null) }
        var err: String? = null
        val airMap = mutableMapOf<String, JsonObject>()
        val rows = BigIslandLocation.entries.map { loc ->
            val allow = pro || loc.id == FREE_TIER_LOC_ID
            if (!allow) {
                loc to null
            } else {
                val r = repo.observeOfflineFirst(loc.id, loc.latitude, loc.longitude, force)
                err = err ?: r.exceptionOrNull()?.message
                airQualityRepo.offlineFirst(loc.id, loc.latitude, loc.longitude, force)
                    .getOrNull()
                    ?.let { airMap[loc.id] = it }
                loc to r.getOrNull()
            }
        }
        var gps: JsonObject? = null
        val lat = lastGpsLat
        val lon = lastGpsLon
        if (pro && prefs.weatherUseMyLocation.first() && lat != null && lon != null) {
            val r = repo.observeOfflineFirst("gps", lat, lon, force)
            err = err ?: r.exceptionOrNull()?.message
            gps = r.getOrNull()
            if (gps != null) prefs.setWeatherLastGps(lat, lon)
            airQualityRepo.offlineFirst("gps", lat, lon, force)
                .getOrNull()
                ?.let { airMap["gps"] = it }
        }
        _state.update {
            it.copy(loading = false, rows = rows, gpsWeather = gps, airByLocationId = airMap, error = err)
        }
    }

    fun loadGpsWeather(lat: Double, lon: Double, force: Boolean = false) {
        viewModelScope.launch {
            if (!prefs.authProUnlocked.first()) return@launch
            if (!prefs.weatherUseMyLocation.first()) return@launch
            lastGpsLat = lat
            lastGpsLon = lon
            val r = repo.observeOfflineFirst("gps", lat, lon, force)
            r.getOrNull()?.let { prefs.setWeatherLastGps(lat, lon) }
            val air = airQualityRepo.offlineFirst("gps", lat, lon, force).getOrNull()
            _state.update {
                val airMap = it.airByLocationId.toMutableMap()
                if (air != null) airMap["gps"] = air
                it.copy(
                    gpsWeather = r.getOrNull(),
                    airByLocationId = airMap,
                    error = it.error ?: r.exceptionOrNull()?.message,
                )
            }
        }
    }
}
