package com.music.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppSpacing {
    val XXSmall = 4.dp
    val XSmall = 8.dp
    val SmallMedium = 10.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 20.dp
    val XLarge = 24.dp
}

object AppShapes {
    val Tiny = 4.dp
    val ExtraSmall = 8.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 24.dp
    val XLarge = 28.dp
}

object AppElevation {
    val None = 0.dp
    val Subtle = 1.dp
    val Low = 4.dp
    val Medium = 8.dp
}

object AppIconSize {
    val Small = 16.dp
    val Medium = 20.dp
    val Large = 24.dp
}

enum class AppSurfaceTone {
    Plain,
    Elevated,
    Glass
}

@Composable
fun appSurfaceColor(tone: AppSurfaceTone): Color = when (tone) {
    AppSurfaceTone.Plain -> MaterialTheme.colorScheme.surfaceContainer
    AppSurfaceTone.Elevated -> MaterialTheme.colorScheme.surfaceContainerHigh
    AppSurfaceTone.Glass -> LocalGlassColors.current.surface
}

@Composable
fun appSurfaceBorderColor(tone: AppSurfaceTone): Color = when (tone) {
    AppSurfaceTone.Plain -> MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    AppSurfaceTone.Elevated -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    AppSurfaceTone.Glass -> LocalGlassColors.current.border
}
