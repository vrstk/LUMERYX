package com.lumeryx.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lumeryx.app.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F52AA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DFFF),
    onPrimaryContainer = Color(0xFF1B1C63),
    secondary = Color(0xFF5B5B73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1DFF9),
    onSecondaryContainer = Color(0xFF292943),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1B1B24),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1B1B24),
    surfaceVariant = Color(0xFFE4E1EE),
    onSurfaceVariant = Color(0xFF484654),
    surfaceContainer = Color(0xFFF0EDF7),
    surfaceContainerHigh = Color(0xFFEBE7F2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC0C1FF),
    onPrimary = Color(0xFF242675),
    primaryContainer = Color(0xFF393B90),
    onPrimaryContainer = Color(0xFFE2DFFF),
    secondary = Color(0xFFC4C2DD),
    onSecondary = Color(0xFF2E2E46),
    secondaryContainer = Color(0xFF44435C),
    onSecondaryContainer = Color(0xFFE1DFF9),
    background = Color(0xFF14131B),
    onBackground = Color(0xFFE6E2EE),
    surface = Color(0xFF14131B),
    onSurface = Color(0xFFE6E2EE),
    surfaceVariant = Color(0xFF484654),
    onSurfaceVariant = Color(0xFFC9C5D4),
    surfaceContainer = Color(0xFF211F2A),
    surfaceContainerHigh = Color(0xFF2B2934),
)

@Composable
internal fun useDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
internal fun LumeryxTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
