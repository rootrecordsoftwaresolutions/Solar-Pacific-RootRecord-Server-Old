package com.rootrecord.kilauea.alerts.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AirQualitySnap(
    val aqi: Int,
    val category: String,
    val categoryColor: Color,
    val dominantPollutant: String?,
    val pm25: String?,
    val pm10: String?,
)

fun parseAirQualitySnap(air: JsonObject): AirQualitySnap? {
    val cur = air["current"]?.jsonObject ?: return null
    val aqi = cur["overallIndex"]?.jsonPrimitive?.doubleOrNull?.toInt()
        ?: cur["us_aqi"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
        ?: return null
    val category = cur["category"]?.jsonPrimitive?.contentOrNull
        ?: usAqiCategory(aqi)
    val colorHex = cur["categoryColor"]?.jsonPrimitive?.contentOrNull
    val dominant = cur["dominantPollutant"]?.jsonPrimitive?.contentOrNull
    val pm25v = cur["pm2_5_ug_m3"]?.jsonPrimitive?.contentOrNull
        ?: pollutantValue(cur, "PM2.5")
    val pm10v = cur["pm10_ug_m3"]?.jsonPrimitive?.contentOrNull
        ?: pollutantValue(cur, "PM10")
    return AirQualitySnap(
        aqi = aqi,
        category = category,
        categoryColor = parseAqiColor(colorHex, aqi),
        dominantPollutant = dominant?.takeIf { it.isNotBlank() },
        pm25 = pm25v,
        pm10 = pm10v,
    )
}

private fun pollutantValue(cur: JsonObject, name: String): String? {
    val arr = cur["pollutants"]?.jsonArray ?: return null
    for (el in arr) {
        val o = el.jsonObject
        val n = o["name"]?.jsonPrimitive?.contentOrNull ?: o["type"]?.jsonPrimitive?.contentOrNull
        if (n.equals(name, ignoreCase = true)) {
            val v = o["value"]?.jsonPrimitive?.contentOrNull ?: return null
            val unit = o["unit"]?.jsonPrimitive?.contentOrNull ?: "μg/m³"
            return "$v $unit"
        }
    }
    return null
}

fun briefAirQualityLine(air: JsonObject): String? {
    val snap = parseAirQualitySnap(air) ?: return null
    return "Air: US AQI ${snap.aqi} (${snap.category})"
}

fun usAqiCategory(aqi: Int): String = when {
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy for sensitive groups"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

private fun parseAqiColor(hex: String?, aqi: Int): Color {
    val h = hex?.trim().orEmpty()
    if (h.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
        return Color(android.graphics.Color.parseColor(h))
    }
    return when {
        aqi <= 50 -> Color(0xFF00E400)
        aqi <= 100 -> Color(0xFFFFFF00)
        aqi <= 150 -> Color(0xFFFF7E00)
        aqi <= 200 -> Color(0xFFFF0000)
        aqi <= 300 -> Color(0xFF8F3F97)
        else -> Color(0xFF7E0023)
    }
}

@Composable
fun AirQualityBentoCard(air: JsonObject?, modifier: Modifier = Modifier) {
    val snap = air?.let { parseAirQualitySnap(it) }
    if (snap == null) return
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AIR QUALITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                snap.dominantPollutant?.let {
                    Text("Dominant: $it", style = MaterialTheme.typography.bodySmall)
                }
                val parts = listOfNotNull(
                    snap.pm25?.let { "PM2.5 $it" },
                    snap.pm10?.let { "PM10 $it" },
                )
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                air["model_note"]?.jsonPrimitive?.contentOrNull?.let { note ->
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                air["observed_at_note"]?.jsonPrimitive?.contentOrNull?.let { note ->
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                Modifier
                    .background(snap.categoryColor.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    snap.aqi.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF0A0A0A),
                )
                Text(
                    snap.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0A0A0A).copy(alpha = 0.85f),
                )
            }
        }
    }
}
