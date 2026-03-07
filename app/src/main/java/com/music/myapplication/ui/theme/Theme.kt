package com.music.myapplication.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = QQMusicGreen,
    onPrimary = Color.White,
    primaryContainer = QQMusicGreenLight,
    onPrimaryContainer = Color(0xFF002110),
    secondary = QQMusicGreenDark,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerLow = Color(0xFFF7F7F7)
)

private val DarkColorScheme = darkColorScheme(
    primary = QQMusicGreenLight,
    onPrimary = Color(0xFF003919),
    primaryContainer = QQMusicGreen,
    onPrimaryContainer = Color.White,
    secondary = QQMusicGreenLight,
    onSecondary = Color(0xFF003919),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    surfaceContainer = Color(0xFF2C2C2E),
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerLow = Color(0xFF1C1C1E)
)

@Immutable
data class GlassColors(
    val surface: Color,
    val border: Color,
    val scrim: Color
)

val LocalGlassColors = staticCompositionLocalOf {
    GlassColors(
        surface = GlassSurfaceDark,
        border = GlassBorderDark,
        scrim = PlayerScrimDark
    )
}

@Composable
fun MusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val glassColors = if (darkTheme) {
        GlassColors(
            surface = GlassSurfaceDark,
            border = GlassBorderDark,
            scrim = PlayerScrimDark
        )
    } else {
        GlassColors(
            surface = GlassSurfaceLight,
            border = GlassBorderLight,
            scrim = PlayerScrimLight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalGlassColors provides glassColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
