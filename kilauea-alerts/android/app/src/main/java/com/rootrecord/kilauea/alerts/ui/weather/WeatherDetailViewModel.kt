package com.rootrecord.kilauea.alerts.ui.weather

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.AirQualityRepository
import com.rootrecord.kilauea.alerts.data.repository.WeatherRepository
import com.rootrecord.kilauea.alerts.domain.BigIslandLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class WeatherDetailUiState(
    val bundle: JsonObject? = null,
    val airQuality: JsonObject? = null,
    val error: String? = null,
)

@HiltViewModel
class WeatherDetailViewModel @Inject constructor(
    private val repo: WeatherRepository,
    private val airQualityRepo: AirQualityRepository,
    private val prefs: KilaueaPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val locationId: String = savedStateHandle.get<String>("id") ?: ""

    private val _state = MutableStateFlow(WeatherDetailUiState())
    val state = _state.asStateFlow()

    fun load(force: Boolean) {
        viewModelScope.launch {
            when (locationId) {
                WEATHER_GPS_ROUTE_ID -> {
                    val gps = prefs.getWeatherLastGps()
                    if (gps == null) {
                        _state.update {
                            it.copy(
                                bundle = null,
                                error = "Enable “My Location Weather” and allow location on the Weather tab so we can save your coordinates, then try again.",
                            )
                        }
                        return@launch
                    }
                    val (lat, lon) = gps
                    val r = repo.observeOfflineFirst(WEATHER_GPS_ROUTE_ID, lat, lon, force)
                    val air = airQualityRepo.offlineFirst(WEATHER_GPS_ROUTE_ID, lat, lon, force).getOrNull()
                    _state.update {
                        WeatherDetailUiState(
                            bundle = r.getOrNull(),
                            airQuality = air,
                            error = r.exceptionOrNull()?.message,
                        )
                    }
                }
                else -> {
                    val loc = BigIslandLocation.byId(locationId) ?: run {
                        _state.update { it.copy(error = "Unknown location", bundle = null) }
                        return@launch
                    }
                    val r = repo.observeOfflineFirst(loc.id, loc.latitude, loc.longitude, force)
                    val air = airQualityRepo.offlineFirst(loc.id, loc.latitude, loc.longitude, force).getOrNull()
                    _state.update {
                        WeatherDetailUiState(
                            bundle = r.getOrNull(),
                            airQuality = air,
                            error = r.exceptionOrNull()?.message,
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Matches [WeatherRepository] cache key / API `locationId` for GPS dashboard rows. */
        const val WEATHER_GPS_ROUTE_ID = "gps"
    }
}
