package com.rootrecord.rootmc.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatting {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateTimeFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())

    /** Wall-clock time in the device's local timezone. */
    fun formatEpochMillis(millis: Long): String {
        if (millis <= 0L) return ""
        return Instant.ofEpochMilli(millis).atZone(zone).format(dateTimeFormatter)
    }

    /** ISO-8601 or epoch string from APIs → local time, or null if unparseable. */
    fun formatApiTimestamp(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return formatEpochMillis(it) }
        return runCatching {
            Instant.parse(s).atZone(zone).format(dateTimeFormatter)
        }.getOrNull()
    }
}

@Composable
fun EntryTimestamp(
    epochMillis: Long,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val text = remember(epochMillis) {
        val formatted = TimeFormatting.formatEpochMillis(epochMillis)
        if (formatted.isEmpty()) return@remember null
        if (prefix.isNullOrBlank()) formatted else "$prefix $formatted"
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = modifier,
        )
    }
}

@Composable
fun EntryTimestampFromApi(
    raw: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val text = remember(raw) {
        val formatted = TimeFormatting.formatApiTimestamp(raw) ?: return@remember null
        if (prefix.isNullOrBlank()) formatted else "$prefix $formatted"
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = modifier,
        )
    }
}
