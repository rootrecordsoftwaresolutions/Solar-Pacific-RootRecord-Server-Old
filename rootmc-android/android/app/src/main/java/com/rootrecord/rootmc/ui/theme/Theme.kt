package com.rootrecord.rootmc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RootMCDarkColors = darkColorScheme(
    primary = RmcGold,
    onPrimary = Color(0xFF1A1308),
    primaryContainer = RmcGoldDim,
    onPrimaryContainer = TextPrimary,
    secondary = RmcEmerald,
    onSecondary = Color(0xFF0A1F16),
    tertiary = RmcSurfaceElevated,
    background = RmcBg,
    onBackground = TextPrimary,
    surface = RmcSurface,
    onSurface = TextPrimary,
    surfaceVariant = RmcSurfaceVariant,
    onSurfaceVariant = TextMuted,
    error = RmcRed,
    onError = Color.Black,
)

private val RootMCLightColors = lightColorScheme(
    primary = RmcGold,
    onPrimary = Color(0xFF1A1308),
    primaryContainer = Color(0xFFF5DFC1),
    onPrimaryContainer = Color(0xFF3A2A0E),
    secondary = RmcEmerald,
    onSecondary = Color(0xFF0F2A1E),
    tertiary = RmcGoldBright,
    background = RootMCLightBg,
    onBackground = RootMCLightText,
    surface = RootMCLightSurface,
    onSurface = RootMCLightText,
    surfaceVariant = RootMCLightSurfaceVariant,
    onSurfaceVariant = RootMCLightTextMuted,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun RootMCTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) RootMCDarkColors else RootMCLightColors,
        content = content,
    )
}
