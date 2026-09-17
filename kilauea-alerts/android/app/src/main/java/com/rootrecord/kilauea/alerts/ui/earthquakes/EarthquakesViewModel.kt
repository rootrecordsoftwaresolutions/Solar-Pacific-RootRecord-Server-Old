package com.rootrecord.kilauea.alerts.ui.earthquakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.repository.EarthquakeRepository
import com.rootrecord.kilauea.alerts.notifications.AlertDiffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class EqRow(
    val id: String?,
    val mag: Double?,
    val place: String?,
    val timeMs: Long?,
    val url: String?,
    val lat: Double?,
    val lon: Double?,
)

data class EarthquakesUiState(
    val loading: Boolean = false,
    val geo: JsonObject? = null,
    val rows: List<EqRow> = emptyList(),
    val error: String? = null,
    /** Shown only after an explicit user refresh when the feed matches what was already displayed. */
    val snackbarMessage: String? = null,
)

@HiltViewModel
class EarthquakesViewModel @Inject constructor(
    private val repo: EarthquakeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EarthquakesUiState())
    val state = _state.asStateFlow()

    /**
     * @param forceRefresh When true, fetches the latest list from the USGS. When false, prefers data already on the device.
     * @param showSnackbarIfUnchanged Only for explicit FAB refresh; avoids a misleading snackbar after opening from a notification.
     */
    fun load(forceRefresh: Boolean, showSnackbarIfUnchanged: Boolean = false) {
        viewModelScope.launch {
            val previousGeo = _state.value.geo
            _state.update { it.copy(loading = true, error = null, snackbarMessage = null) }
            val r = repo.offlineFirst(forceRefresh)
            val geo = r.getOrNull()
            val snackbarMessage =
                if (showSnackbarIfUnchanged && forceRefresh && r.isSuccess && previousGeo != null && geo != null) {
                    if (featureIdSet(previousGeo) == featureIdSet(geo)) {
                        "No new updates"
                    } else {
                        null
                    }
                } else {
                    null
                }
            _state.update {
                it.copy(
                    loading = false,
                    geo = geo,
                    rows = geo?.let(::parseRows) ?: emptyList(),
                    error = r.exceptionOrNull()?.message,
                    snackbarMessage = snackbarMessage,
                )
            }
        }
    }

    fun consumeSnackbarMessage() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun featureIdSet(geo: JsonObject): Set<String> {
        val feats = geo["features"]?.jsonArray ?: JsonArray(emptyList())
        return feats.mapNotNull { f ->
            AlertDiffer.stableEarthquakeFeatureId(f.jsonObject)
        }.toSet()
    }

    private fun parseRows(geo: JsonObject): List<EqRow> {
        val feats = geo["features"]?.jsonArray ?: JsonArray(emptyList())
        return feats.mapNotNull { f ->
            val o = f.jsonObject
            val props = o["properties"]?.jsonObject ?: return@mapNotNull null
            val geom = o["geometry"]?.jsonObject
            val coords = geom?.get("coordinates")?.jsonArray
            val lon = coords?.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull()
            val lat = coords?.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull()
            EqRow(
                id = o["id"]?.jsonPrimitive?.content,
                mag = props["mag"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                place = props["place"]?.jsonPrimitive?.content,
                timeMs = props["time"]?.jsonPrimitive?.content?.toLongOrNull(),
                url = props["url"]?.jsonPrimitive?.content,
                lat = lat,
                lon = lon,
            )
        }.sortedByDescending { it.timeMs ?: 0L }
    }
}
