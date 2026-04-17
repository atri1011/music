package com.music.myapplication.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal fun fullScreenPlayerBaseColor(isDark: Boolean): Color =
    if (isDark) Color.Black else Color(0xFF1A1A2E)

internal fun fullScreenPlayerTintedBaseColor(baseColor: Color, dominantColor: Color): Color =
    lerp(baseColor, dominantColor, 0.05f)

internal fun fullScreenPlayerOverlayColors(tintedBase: Color): List<Color> = listOf(
    tintedBase.copy(alpha = 0.4f),
    tintedBase.copy(alpha = 0.75f),
    tintedBase.copy(alpha = 0.9f)
)
