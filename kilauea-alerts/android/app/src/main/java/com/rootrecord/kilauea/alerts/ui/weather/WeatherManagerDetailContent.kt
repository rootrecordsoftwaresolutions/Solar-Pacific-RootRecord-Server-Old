package com.rootrecord.kilauea.alerts.ui.weather

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rootrecord.kilauea.alerts.ui.util.AirQualityBentoCard
import com.rootrecord.kilauea.alerts.ui.util.formatDualFahrenheitPrimary
import com.rootrecord.kilauea.alerts.ui.util.formatWeatherTimestampForDisplay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mirrors Weather Manager [Home.js](weather-manager-mobile/frontend/src/pages/Home.js):
 * hero, bento metrics, hourly strip, NOAA/Canada alerts, nearby USGS events.
 */
@Composable
fun WeatherManagerDetailContent(
    bundle: JsonObject,
    airQuality: JsonObject? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val model = parseWeatherManagerHome(bundle)
    var selectedAlert by remember { mutableStateOf<AlertRow?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        model.fetchedAt?.let { iso ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "Last updated: ${formatWeatherTimestampForDisplay(iso) ?: iso}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        val heroParts = model.heroTemp.split(" (", limit = 2)
                        Text(
                            heroParts[0],
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (heroParts.size == 2) {
                            Text(
                                "(${heroParts[1]}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            model.condition,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        model.highFormatted?.let {
                            Text("HIGH $it", style = MaterialTheme.typography.labelMedium)
                        }
                        model.lowFormatted?.let {
                            Text("LOW $it", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Wind", model.wind, model.windDir, Modifier.weight(1f))
            BentoTile("Humidity", model.humidity, null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Pressure", model.pressure, null, Modifier.weight(1f))
            BentoTile("Visibility", model.visibility, null, Modifier.weight(1f))
        }

        AirQualityBentoCard(airQuality, Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Feels like", model.feelsLike, null, Modifier.weight(1f))
            BentoTile("Dew point", model.dewPoint, null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Wet bulb", model.wetBulb, null, Modifier.weight(1f))
            BentoTile("Gusts", model.gusts, null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Cloud cover", model.cloud, null, Modifier.weight(1f))
            BentoTile("UV index", model.uv, null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Precip (1h)", model.precip1h, null, Modifier.weight(1f))
            BentoTile("Precip (3h)", model.precip3h, null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile("Precip (6h)", model.precip6h, null, Modifier.weight(1f))
            BentoTile("Ceiling", model.ceiling, null, Modifier.weight(1f))
        }

        if (model.hourlyRows.isNotEmpty()) {
            Text(
                "NEXT 12 HOURS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.hourlyRows, key = { it.key }) { row ->
                    Card(
                        Modifier.widthIn(min = 88.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(row.timeLabel, style = MaterialTheme.typography.labelSmall)
                            Text(row.temp, style = MaterialTheme.typography.titleMedium)
                            Text(
                                row.shortForecast,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (model.alerts.isNotEmpty()) {
            Text(
                "ACTIVE ALERTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Card(Modifier.fillMaxWidth()) {
                Column {
                    model.alerts.take(8).forEachIndexed { idx, a ->
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedAlert = a },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "${a.severityLabel} · ${a.providerLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(a.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    a.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(a.timeLine, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (idx < model.alerts.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        selectedAlert?.let { alert ->
            AlertDetailDialog(
                alert = alert,
                onDismiss = { selectedAlert = null },
                onOpenLink = alert.detailUrl?.let { url ->
                    {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        selectedAlert = null
                    }
                },
            )
        }

        if (model.usgsEvents.isNotEmpty()) {
            Text(
                "RECENT EARTHQUAKES NEARBY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Card(Modifier.fillMaxWidth()) {
                Column {
                    val eqRows = model.usgsEvents.take(6)
                    eqRows.forEachIndexed { idx, e ->
                        val url = usgsEventDetailUrl(e.json)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = url != null) {
                                    url?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(color = magnitudeTintComposable(e.magnitude)) {
                                Text(
                                    "%.1f".format(e.magnitude),
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(e.place, style = MaterialTheme.typography.bodyMedium)
                                Text(e.subtitle, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (idx < eqRows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (model.periodBullets.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Multi-day forecast", style = MaterialTheme.typography.titleMedium)
                    model.periodBullets.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoTile(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private data class HourlyRow(val key: String, val timeLabel: String, val temp: String, val shortForecast: String)

private data class AlertRow(
    val severityLabel: String,
    val providerLabel: String,
    val title: String,
    val preview: String,
    val timeLine: String,
    val body: String,
    val headlineExtra: String?,
    val instruction: String?,
    val areaDesc: String?,
    val detailUrl: String?,
)

@Composable
private fun AlertDetailDialog(
    alert: AlertRow,
    onDismiss: () -> Unit,
    onOpenLink: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(alert.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${alert.severityLabel} · ${alert.providerLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(alert.timeLine, style = MaterialTheme.typography.labelSmall)
                alert.areaDesc?.let {
                    Text("Area", style = MaterialTheme.typography.labelMedium)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                alert.headlineExtra?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (alert.body.isNotBlank()) {
                    Text(alert.body, style = MaterialTheme.typography.bodySmall)
                }
                alert.instruction?.let {
                    Text("What to do", style = MaterialTheme.typography.labelMedium)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (onOpenLink != null) {
                TextButton(onClick = onOpenLink) { Text("Open link") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private data class UsgsRow(val magnitude: Double, val place: String, val subtitle: String, val json: JsonObject)

private data class WeatherManagerModel(
    val fetchedAt: String?,
    val heroTemp: String,
    val condition: String,
    val highFormatted: String?,
    val lowFormatted: String?,
    val wind: String,
    val windDir: String?,
    val humidity: String,
    val pressure: String,
    val visibility: String,
    val feelsLike: String,
    val dewPoint: String,
    val wetBulb: String,
    val gusts: String,
    val cloud: String,
    val uv: String,
    val precip1h: String,
    val precip3h: String,
    val precip6h: String,
    val ceiling: String,
    val hourlyRows: List<HourlyRow>,
    val alerts: List<AlertRow>,
    val usgsEvents: List<UsgsRow>,
    val periodBullets: List<String>,
)

private fun isDaytimePeriod(o: JsonObject, wantDay: Boolean): Boolean {
    val prim = o["isDaytime"]?.jsonPrimitive ?: return false
    val b = prim.booleanOrNull ?: when (prim.content.lowercase(Locale.US)) {
        "true" -> true
        "false" -> false
        else -> return false
    }
    return b == wantDay
}

private fun usgsEventDetailUrl(o: JsonObject): String? {
    val url = o["url"]?.jsonPrimitive?.contentOrNull?.trim()
    if (!url.isNullOrBlank() && url.startsWith("http", ignoreCase = true)) return url
    val raw = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val slug = raw.substringAfterLast('/').ifBlank { raw }
    return "https://earthquake.usgs.gov/earthquakes/eventpage/${Uri.encode(slug)}"
}

private fun parseWeatherManagerHome(bundle: JsonObject): WeatherManagerModel {
    val obs = bundle["current"]?.jsonObject?.get("observation")?.jsonObject ?: JsonObject(emptyMap())
    val hourlyNow = bundle["current"]?.jsonObject?.get("hourly_now")?.jsonObject ?: JsonObject(emptyMap())

    val tempC = obs.scalarCelsius("temperature")
    val feelsC = obs.scalarCelsius("feelsLike") ?: hourlyNow.scalarCelsius("feelsLike")
    val dewC = obs.scalarCelsius("dewpoint") ?: hourlyNow.scalarCelsius("dewPoint")
    val wetC = obs.scalarCelsius("wetBulbTemperature") ?: hourlyNow.scalarCelsius("wetBulb")
    val humidityPct = obs.scalarPercent("relativeHumidity") ?: hourlyNow.nestedPercent("relativeHumidity")
    val windKmh = obs.scalarDouble("windSpeed") ?: hourlyNow.scalarDouble("windSpeed")
    val gustKmh = obs.scalarDouble("windGust")
    val pressurePa = obs.scalarDouble("barometricPressure")
    val cloudPct = obs.scalarDouble("cloudCover")
    val uv = obs["uvIndex"]?.jsonPrimitive?.contentOrNull ?: hourlyNow["uvIndex"]?.jsonPrimitive?.contentOrNull
    val precip1h = obs.scalarDouble("precip1h")
    val precip3h = obs.scalarDouble("precipPast3h")
    val precip6h = obs.scalarDouble("precipPast6h")
    val ceilingM = obs.scalarDouble("ceiling")
    val visibilityM = obs.scalarDouble("visibility")

    val windDirCardinal = windDirectionLabel(obs, hourlyNow)

    val condition = hourlyNow["shortForecast"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        ?: obs["textDescription"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: "—"

    val forecast = bundle["forecast"]?.jsonObject
    val periods = forecast?.get("periods")?.jsonArray ?: JsonArray(emptyList())
    val hourlyGridUnits = when (forecast?.get("hourly_grid_units")?.jsonPrimitive?.contentOrNull?.lowercase()) {
        "si" -> "si"
        else -> "us"
    }

    val gridHigh = periods.firstOrNull { isDaytimePeriod(it.jsonObject, true) }
        ?.jsonObject?.get("temperature")?.jsonPrimitive?.content?.toDoubleOrNull()
    val gridLow = periods.firstOrNull { isDaytimePeriod(it.jsonObject, false) }
        ?.jsonObject?.get("temperature")?.jsonPrimitive?.content?.toDoubleOrNull()
    val periodUnit = periods.firstOrNull()?.jsonObject?.get("temperatureUnit")?.jsonPrimitive?.content
        ?.trim()?.uppercase(Locale.US)?.takeIf { it == "C" || it == "F" } ?: "F"

    val hourlyTempRaw = hourlyNow["temperature"]?.jsonPrimitive?.contentOrNull
    val hourlyTemp = hourlyTempRaw?.toDoubleOrNull()
    val hourlyTempUnit = hourlyNow["temperatureUnit"]?.jsonPrimitive?.contentOrNull

    val (adjHigh, adjLow) = alignDailyHighLowWithNow(
        gridHigh,
        gridLow,
        periodUnit,
        tempC,
        hourlyTemp,
        hourlyTempUnit,
    )

    val heroTemp = when {
        tempC != null -> formatDualFahrenheitPrimary(tempC, null) ?: "—"
        hourlyTemp != null -> fmtHourlyGridTemp(hourlyTempRaw ?: "", hourlyTempUnit, hourlyGridUnits)
        else -> "—"
    }

    val highFmt = adjHigh?.let { fmtTempFromPeriod(it, periodUnit) }
    val lowFmt = adjLow?.let { fmtTempFromPeriod(it, periodUnit) }

    val hourlyForecast = forecast?.get("hourly")?.jsonArray
    val hourlyRows = hourlyForecast?.take(12)?.mapIndexedNotNull { idx, el ->
        val p = el.jsonObject
        val st = p["startTime"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
        val tlab = runCatching {
            SimpleDateFormat("h a", Locale.US).format(Date.from(java.time.Instant.parse(st)))
        }.getOrElse { st }
        val short = p["shortForecast"]?.jsonPrimitive?.contentOrNull ?: ""
        val tempDisp = fmtHourlyGridTempFromPeriod(p, hourlyGridUnits)
        HourlyRow(key = p["number"]?.toString() ?: "h-$idx", timeLabel = tlab, temp = tempDisp, shortForecast = short)
    }.orEmpty()

    val alertsObj = bundle["alerts"]?.jsonObject
    val canadaObj = bundle["canada_alerts"]?.jsonObject
    val src = alertsObj?.get("source")?.jsonPrimitive?.contentOrNull?.lowercase(Locale.US).orEmpty()
    val inferred = when {
        src == "accuweather" -> "accuweather"
        src == "noaa" -> "noaa"
        else -> "noaa"
    }
    val noaaList = alertsObj?.get("alerts")?.jsonArray ?: JsonArray(emptyList())
    val caList = canadaObj?.get("alerts")?.jsonArray ?: JsonArray(emptyList())
    val alerts = buildList {
        noaaList.forEach { add(alertRow(it.jsonObject, inferred, bundle)) }
        caList.forEach { add(alertRow(it.jsonObject, "canada", bundle)) }
    }

    val usgsArr = bundle["usgs"]?.jsonObject?.get("events")?.jsonArray
    val usgsEvents = usgsArr?.mapNotNull { e ->
        val o = e.jsonObject
        val magEl = o["magnitude"] ?: return@mapNotNull null
        val mag = (magEl as? JsonPrimitive)?.let { p ->
            p.content.toDoubleOrNull() ?: p.doubleOrNull
        } ?: return@mapNotNull null
        val place = o["place"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val depth = o["depth_km"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val dist = o["distance_miles"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val sub = buildString {
            if (depth != null) append("%.0f km · ".format(depth))
            if (dist != null) append("%.0f mi away".format(dist))
        }.trim().ifBlank { "" }
        UsgsRow(mag, place, sub, o)
    }.orEmpty()

    val periodBullets = periods.take(8).mapNotNull { p ->
        val o = p.jsonObject
        val name = o["name"]?.jsonPrimitive?.contentOrNull
        val t = o["temperature"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val tu = o["temperatureUnit"]?.jsonPrimitive?.contentOrNull ?: "F"
        val tempFmt = t?.let { fmtTempFromPeriod(it, tu) }
        val short = o["shortForecast"]?.jsonPrimitive?.contentOrNull
            ?: o["detailedForecast"]?.jsonPrimitive?.contentOrNull?.take(120)
        listOfNotNull(name, tempFmt, short).joinToString(" — ").takeIf { it.isNotBlank() }
    }

    val fetchedAt = bundle["fetched_at"]?.jsonPrimitive?.contentOrNull

    return WeatherManagerModel(
        fetchedAt = fetchedAt,
        heroTemp = heroTemp,
        condition = condition,
        highFormatted = highFmt,
        lowFormatted = lowFmt,
        wind = fmtSpeedMph(windKmh),
        windDir = windDirCardinal,
        humidity = humidityPct?.let { "${it.roundToInt()}%" } ?: "—",
        pressure = pressurePa?.let { "%.0f hPa".format(it / 100.0) } ?: "—",
        visibility = visibilityM?.let { fmtKmOrMi(it / 1000.0) } ?: "—",
        feelsLike = feelsC?.let { formatDualFahrenheitPrimary(it, null) ?: "—" } ?: "—",
        dewPoint = dewC?.let { formatDualFahrenheitPrimary(it, null) ?: "—" } ?: "—",
        wetBulb = wetC?.let { formatDualFahrenheitPrimary(it, null) ?: "—" } ?: "—",
        gusts = fmtSpeedMph(gustKmh),
        cloud = cloudPct?.let { "${it.roundToInt()}%" } ?: "—",
        uv = uv?.takeIf { it.isNotBlank() } ?: "—",
        precip1h = fmtMmOrIn(precip1h),
        precip3h = fmtMmOrIn(precip3h),
        precip6h = fmtMmOrIn(precip6h),
        ceiling = ceilingM?.let { fmtMOrFt(it) } ?: "—",
        hourlyRows = hourlyRows,
        alerts = alerts,
        usgsEvents = usgsEvents,
        periodBullets = periodBullets,
    )
}

private fun JsonObject.scalarDouble(key: String): Double? {
    val v = this[key] ?: return null
    return when (v) {
        is JsonPrimitive -> v.content.toDoubleOrNull()
        is JsonObject -> v["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
        else -> null
    }
}

private fun JsonObject.scalarCelsius(key: String): Double? {
    val raw = this[key] ?: return null
    return when (raw) {
        is JsonPrimitive -> raw.content.toDoubleOrNull()
        is JsonObject -> raw["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
        else -> null
    }
}

private fun JsonObject.scalarPercent(key: String): Double? {
    val raw = this[key] ?: return null
    return when (raw) {
        is JsonPrimitive -> raw.content.toDoubleOrNull()
        is JsonObject -> raw["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
        else -> null
    }
}

private fun JsonObject.nestedPercent(key: String): Double? {
    val rel = this["relativeHumidity"]?.jsonObject ?: return null
    return rel["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
}

private fun windDirectionLabel(obs: JsonObject, hourlyNow: JsonObject): String? {
    val degObj = obs["windDirection"]?.jsonObject?.get("value") ?: obs["windDirection"]
    val deg = when (degObj) {
        is JsonPrimitive -> degObj.content.toDoubleOrNull()
        is JsonObject -> degObj["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
        else -> null
    }
    if (deg != null) return "${deg.roundToInt()}°"
    obs["windDirectionCardinal"]?.jsonPrimitive?.contentOrNull?.let { return it }
    hourlyNow["windDirection"]?.jsonPrimitive?.contentOrNull?.let { return it }
    return null
}

private fun alignDailyHighLowWithNow(
    gridHigh: Double?,
    gridLow: Double?,
    periodUnit: String,
    obsTempC: Double?,
    hourlyTemp: Double?,
    hourlyTempUnit: String?,
): Pair<Double?, Double?> {
    var cur: Double? = null
    if (obsTempC != null) {
        cur = if (periodUnit == "C") obsTempC else obsTempC * 9.0 / 5.0 + 32.0
    } else if (hourlyTemp != null && hourlyTempUnit != null) {
        val t = hourlyTemp
        val hu = hourlyTempUnit.trim().uppercase(Locale.US)
        cur = when {
            hu == "C" && periodUnit == "C" -> t
            hu == "C" -> t * 9.0 / 5.0 + 32.0
            hu == "F" && periodUnit == "F" -> t
            hu == "F" -> (t - 32.0) * 5.0 / 9.0
            else -> null
        }
    }
    if (cur == null || !cur.isFinite()) return Pair(gridHigh, gridLow)
    var h = gridHigh
    var l = gridLow
    if (gridHigh != null && gridHigh.isFinite()) h = max(gridHigh, cur)
    if (gridLow != null && gridLow.isFinite()) l = min(gridLow, cur)
    return Pair(h, l)
}

private fun fmtTempFromPeriod(value: Double, unit: String): String {
    val u = unit.trim().uppercase(Locale.US)
    return when (u) {
        "C" -> formatDualFahrenheitPrimary(value, null) ?: "${value.roundToInt()}°"
        else -> {
            val f = value
            val c = (f - 32.0) * 5.0 / 9.0
            formatDualFahrenheitPrimary(c, f) ?: "${f.roundToInt()}°F"
        }
    }
}

private fun fmtHourlyGridTemp(raw: String, unitHint: String?, hourlyGridUnits: String): String {
    val n = raw.toDoubleOrNull() ?: return "—"
    var letter = unitHint?.trim()?.uppercase(Locale.US)?.takeIf { it == "C" || it == "F" }
    if (letter == null) letter = if (hourlyGridUnits == "si") "C" else "F"
    return fmtTempFromPeriod(n, letter)
}

private fun fmtHourlyGridTempFromPeriod(p: JsonObject, hourlyGridUnits: String): String {
    val n = p["temperature"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return "—"
    var letter = p["temperatureUnit"]?.jsonPrimitive?.content?.trim()?.uppercase(Locale.US)
    if (letter != "C" && letter != "F") {
        letter = if (hourlyGridUnits == "si") "C" else "F"
    }
    return fmtTempFromPeriod(n, letter ?: "F")
}

private fun fmtSpeedMph(kmh: Double?): String {
    if (kmh == null || !kmh.isFinite()) return "—"
    val mph = kmh / 1.609
    return "${mph.roundToInt()} mph"
}

private fun fmtKmOrMi(km: Double): String {
    val mi = km / 1.609
    return "%.1f mi".format(mi)
}

private fun fmtMmOrIn(mm: Double?): String {
    if (mm == null || !mm.isFinite()) return "—"
    val inches = mm / 25.4
    return "%.2f in".format(inches)
}

private fun fmtMOrFt(m: Double): String {
    val ft = m * 3.28084
    return "${ft.roundToInt()} ft"
}

private fun alertRow(a: JsonObject, providerFallback: String, bundle: JsonObject): AlertRow {
    var p = a["provider"]?.jsonPrimitive?.contentOrNull?.lowercase(Locale.US).orEmpty()
    if (p.isBlank()) {
        val src = bundle["alerts"]?.jsonObject?.get("source")?.jsonPrimitive?.contentOrNull?.lowercase(Locale.US)
        p = when (src) {
            "accuweather" -> "accuweather"
            "noaa" -> "noaa"
            else -> providerFallback
        }
    }
    val providerLabel = when (p) {
        "accuweather" -> "AccuWeather"
        "canada" -> "Environment Canada"
        "noaa" -> "NOAA"
        else -> "Weather"
    }
    val sev = a["severity"]
    val severityLabel = when {
        sev is JsonPrimitive && sev.content.toDoubleOrNull() != null -> "Alert"
        else -> sev?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Info"
    }
    val eventText = coerceAlertNarrative(a["event"]).trim()
    val headlineText = coerceAlertNarrative(a["headline"]).trim()
    val descText = coerceAlertNarrative(a["description"]).let { Regex("\\s+").replace(it, " ") }.trim()
    val title = eventText.takeIf { it.isNotBlank() }
        ?: headlineText.takeIf { it.isNotBlank() }
        ?: "Alert"
    val body = descText.takeIf { it.isNotBlank() } ?: headlineText
    val headlineExtra = headlineText.takeIf { it.isNotBlank() && it != title && it != body }
    val instruction = coerceAlertNarrative(a["instruction"]).trim().takeIf { it.isNotBlank() }
    val areaDesc = coerceAlertNarrative(a["areaDesc"]).trim().takeIf { it.isNotBlank() }
    val detailUrl = a["detailUrl"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?: a["uri"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?: a["web"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
    val preview = body.ifBlank { headlineText }.ifBlank { "—" }
    val clipped = if (preview.length > 220) preview.take(220) + "…" else preview
    val t = a["effective"]?.jsonPrimitive?.contentOrNull ?: a["sent"]?.jsonPrimitive?.contentOrNull
    val timeLine = t?.let { formatAlertTime(it) } ?: "—"
    return AlertRow(
        severityLabel = severityLabel,
        providerLabel = providerLabel,
        title = title,
        preview = clipped,
        timeLine = timeLine,
        body = body.ifBlank { headlineText },
        headlineExtra = headlineExtra,
        instruction = instruction,
        areaDesc = areaDesc,
        detailUrl = detailUrl,
    )
}

/** AccuWeather may send description/headline as `{ English, Localized }` objects. */
private fun coerceAlertNarrative(el: kotlinx.serialization.json.JsonElement?): String {
    if (el == null) return ""
    if (el is JsonPrimitive) return el.contentOrNull.orEmpty()
    if (el is JsonObject) {
        coerceAlertNarrative(el["English"]).trim().takeIf { it.isNotEmpty() }?.let { return it }
        coerceAlertNarrative(el["Localized"]).trim().takeIf { it.isNotEmpty() }?.let { return it }
        val cat = el["Category"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val typ = el["Type"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return listOf(cat, typ).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    return ""
}

private fun formatAlertTime(iso: String): String =
    runCatching {
        val d = Date.from(java.time.Instant.parse(iso))
        SimpleDateFormat("MMM d, h:mm a", Locale.US).format(d)
    }.getOrElse { iso }

@Composable
private fun magnitudeTintComposable(m: Double): androidx.compose.ui.graphics.Color {
    return when {
        m >= 7.0 -> MaterialTheme.colorScheme.error
        m >= 5.0 -> MaterialTheme.colorScheme.tertiary
        m >= 3.0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}
