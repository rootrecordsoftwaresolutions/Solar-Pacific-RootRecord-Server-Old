package com.rootrecord.kilauea.alerts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val KilaueaDarkColors = darkColorScheme(
    primary = LavaOrange,
    onPrimary = Color(0xFF1A0E0A),
    primaryContainer = EmberRed,
    onPrimaryContainer = TextPrimary,
    secondary = LavaGlow,
    onSecondary = Color.Black,
    tertiary = LavaGlow,
    background = VolcanicBg,
    onBackground = TextPrimary,
    surface = VolcanicSurface,
    onSurface = TextPrimary,
    surfaceVariant = VolcanicSurfaceVariant,
    onSurfaceVariant = TextMuted,
    error = Color(0xFFFF5449),
    onError = Color.Black,
)

private val KilaueaLightColors = lightColorScheme(
    primary = EmberRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3A0900),
    secondary = LavaOrange,
    onSecondary = Color.Black,
    tertiary = LavaGlow,
    background = KilaueaLightBg,
    onBackground = KilaueaLightText,
    surface = KilaueaLightSurface,
    onSurface = KilaueaLightText,
    surfaceVariant = KilaueaLightSurfaceVariant,
    onSurfaceVariant = KilaueaLightTextMuted,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun KilaueaTheme(
    darkTheme: Boolean,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * fontScale.coerceIn(1f, 1.3f),
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = if (darkTheme) KilaueaDarkColors else KilaueaLightColors,
            content = content,
        )
    }
}
