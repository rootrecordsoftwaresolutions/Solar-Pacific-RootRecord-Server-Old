package com.rootrecord.kilauea.alerts.ui.util

import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

private fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0

private fun roundTemp(value: Double): String = String.format("%.1f", value)

/**
 * US-first: °F shown first, °C in parentheses. Uses explicit C/F when both exist.
 */
fun formatDualFahrenheitPrimary(metricC: Double?, imperialF: Double?): String? {
    val c = metricC
    val f = imperialF ?: c?.let { celsiusToFahrenheit(it) }
    return when {
        f != null && c != null -> "${roundTemp(f)}°F (${roundTemp(c)}°C)"
        f != null -> "${roundTemp(f)}°F (${roundTemp(fahrenheitToCelsius(f))}°C)"
        c != null -> "${roundTemp(celsiusToFahrenheit(c))}°F (${roundTemp(c)}°C)"
        else -> null
    }
}

private data class AccuSnap(
    val celsius: Double?,
    val fahrenheit: Double?,
    val condition: String?,
)

private fun accuCurrent(current: JsonObject?): AccuSnap? {
    val raw = current?.get("raw")?.jsonObject ?: return null
    val loc = raw["current"]?.jsonObject ?: raw["Current"]?.jsonObject ?: return null
    val metric = loc["Temperature"]?.jsonObject?.get("Metric")?.jsonObject
    val imperial = loc["Temperature"]?.jsonObject?.get("Imperial")?.jsonObject
    val c = metric?.get("Value")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
    val f = imperial?.get("Value")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
    val text = loc["WeatherText"]?.jsonPrimitive?.contentOrNull
    return AccuSnap(c, f, text)
}

/** Interpret grid reading against station temps when possible; otherwise best-effort guess. */
private fun formatGridTemperature(hourly: JsonObject?, gridRaw: String, accu: AccuSnap?): String? {
    val unitHint = hourly?.get("temperatureUnit")?.jsonPrimitive?.contentOrNull?.uppercase()
    when {
        unitHint?.contains("C") == true ->
            gridRaw.toDoubleOrNull()?.let { formatDualFahrenheitPrimary(it, celsiusToFahrenheit(it)) }
        unitHint?.contains("F") == true ->
            gridRaw.toDoubleOrNull()?.let { formatDualFahrenheitPrimary(fahrenheitToCelsius(it), it) }
        else -> null
    }?.let { return it }

    val g = gridRaw.toDoubleOrNull() ?: return null
    val accuC = accu?.celsius ?: accu?.fahrenheit?.let { fahrenheitToCelsius(it) }
    val accuF = accu?.fahrenheit ?: accu?.celsius?.let { celsiusToFahrenheit(it) }
    if (accuC != null && accuF != null) {
        val errIfGridIsC = abs(g - accuC)
        val errIfGridIsF = abs(g - accuF)
        return if (errIfGridIsC <= errIfGridIsF) {
            formatDualFahrenheitPrimary(g, celsiusToFahrenheit(g))
        } else {
            formatDualFahrenheitPrimary(fahrenheitToCelsius(g), g)
        }
    }

    return formatGridTemperatureGuess(gridRaw)
}

/** When station data is absent: decimals → °C; whole degrees ≥55 → °F; else °C. */
private fun formatGridTemperatureGuess(gridRaw: String): String? {
    val g = gridRaw.toDoubleOrNull() ?: return null
    val hasDecimal = gridRaw.contains('.')
    val asFahrenheit = !hasDecimal && g >= 55.0
    return if (asFahrenheit) {
        formatDualFahrenheitPrimary(fahrenheitToCelsius(g), g)
    } else {
        formatDualFahrenheitPrimary(g, celsiusToFahrenheit(g))
    }
}

private fun headlineTemperature(hourly: JsonObject?, accu: AccuSnap?): String? {
    val raw = hourly?.get("temperature")?.jsonPrimitive?.contentOrNull
    return when {
        raw != null -> formatGridTemperature(hourly, raw, accu)
        accu != null -> formatDualFahrenheitPrimary(accu.celsius, accu.fahrenheit)
        else -> null
    }
}

/** Formats stored timestamps for on-screen labels (readable date/time, not raw ISO). */
fun formatWeatherTimestampForDisplay(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    return try {
        when {
            s.contains('T') -> s.replace('T', ' ').substringBefore('.').substringBefore('Z').take(19)
            else -> s.take(24)
        }
    } catch (_: Exception) {
        s.take(24)
    }
}

/** Single-line summary for weather list rows. */
fun briefWeatherSummary(bundle: JsonObject): String {
    val cur = bundle["current"]?.jsonObject
    val hourly = cur?.get("hourly_now")?.jsonObject
    val accu = accuCurrent(cur)
    val phrase = hourly?.get("shortForecast")?.jsonPrimitive?.contentOrNull
        ?: accu?.condition
    val fetched = bundle["fetched_at"]?.jsonPrimitive?.contentOrNull
    val parts = mutableListOf<String>()
    headlineTemperature(hourly, accu)?.let { parts += it }
    if (phrase != null) parts += phrase
    if (fetched != null) {
        parts += "Updated ${formatWeatherTimestampForDisplay(fetched) ?: fetched}"
    }
    return parts.joinToString(" · ").ifBlank { "Loading…" }
}

data class DashboardDetailSections(
    val fetchedAt: String?,
    val headlineCondition: String?,
    val temperatureLine: String?,
    val windLine: String?,
    val humidityLine: String?,
    val forecastBullets: List<String>,
    val alertSummary: String?,
)

fun parseDashboardDetail(bundle: JsonObject): DashboardDetailSections {
    val fetchedAt = bundle["fetched_at"]?.jsonPrimitive?.contentOrNull
    val cur = bundle["current"]?.jsonObject
    val hourly = cur?.get("hourly_now")?.jsonObject

    val accu = accuCurrent(cur)
    val headline = hourly?.get("shortForecast")?.jsonPrimitive?.contentOrNull ?: accu?.condition
    val obsTemp = hourly?.get("temperature")?.jsonPrimitive?.contentOrNull

    val tempLine = when {
        obsTemp != null && accu != null -> {
            val gridFmt = formatGridTemperature(hourly, obsTemp, accu)
            val stationFmt = formatDualFahrenheitPrimary(accu.celsius, accu.fahrenheit)
            when {
                gridFmt != null && stationFmt != null -> {
                    if (gridFmt == stationFmt) stationFmt else "$stationFmt · $gridFmt"
                }
                stationFmt != null -> stationFmt
                gridFmt != null -> gridFmt
                else -> null
            }
        }
        obsTemp != null -> formatGridTemperature(hourly, obsTemp, accu)
        accu != null -> formatDualFahrenheitPrimary(accu.celsius, accu.fahrenheit)
        else -> null
    }

    val windDir = hourly?.get("windDirection")?.jsonPrimitive?.contentOrNull
    val windSpd = hourly?.get("windSpeed")?.jsonPrimitive?.contentOrNull
    val wind = when {
        windDir != null && windSpd != null -> "$windDir · $windSpd"
        windSpd != null -> windSpd
        else -> null
    }

    val humidity = hourly?.get("relativeHumidity")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull
        ?.let { "$it% humidity" }
        ?: hourly?.get("relativeHumidity")?.jsonPrimitive?.contentOrNull?.let { "$it% humidity" }

    val fc = bundle["forecast"]?.jsonObject
    val periods = fc?.get("periods")?.jsonArray ?: JsonArray(emptyList())
    val bullets = periods.take(6).mapNotNull { p ->
        val o = p.jsonObject
        val name = o["name"]?.jsonPrimitive?.contentOrNull
        val tempFmt = formatDashboardForecastPeriodTemp(o)
        val short = o["shortForecast"]?.jsonPrimitive?.contentOrNull
            ?: o["detailedForecast"]?.jsonPrimitive?.contentOrNull?.take(120)
        listOfNotNull(name, tempFmt, short).joinToString(" — ").takeIf { it.isNotBlank() }
    }

    val alertsObj = bundle["alerts"]?.jsonObject
    val alertList = alertsObj?.get("alerts")?.jsonArray
    val alertSummary = when {
        alertList == null -> null
        alertList.isEmpty() -> "No major weather alerts for this area right now."
        alertList.size == 1 -> "1 active weather alert — open the Weather tab for full text."
        else -> "${alertList.size} active weather alerts — open the Weather tab for full text."
    }

    return DashboardDetailSections(
        fetchedAt = fetchedAt,
        headlineCondition = headline,
        temperatureLine = tempLine,
        windLine = wind,
        humidityLine = humidity,
        forecastBullets = bullets,
        alertSummary = alertSummary,
    )
}

/** Period `temperature` may be a number or NWS-style `{ value, unitCode }`; [temperatureUnit] is `C` for AccuWeather paths. */
private fun JsonObject.forecastPeriodTemperatureC(): Double? {
    val raw = this["temperature"] ?: return null
    val n = when (raw) {
        is JsonPrimitive -> raw.content.toDoubleOrNull()
        is JsonObject -> raw["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
        else -> null
    } ?: return null
    val u = this["temperatureUnit"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase() ?: "F"
    return when {
        u == "C" || u.contains("CELSIUS") || u.contains("DEGC") -> n
        u == "F" || u.contains("FAHREN") -> fahrenheitToCelsius(n)
        else -> fahrenheitToCelsius(n)
    }
}

private fun formatDashboardForecastPeriodTemp(o: JsonObject): String? {
    val c = o.forecastPeriodTemperatureC() ?: return null
    return formatDualFahrenheitPrimary(c, null)
}
