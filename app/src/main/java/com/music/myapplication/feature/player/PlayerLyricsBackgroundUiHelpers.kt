package com.music.myapplication.feature.player

import android.os.Build
import androidx.compose.ui.graphics.Color

internal fun shouldUseNativeBlur(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

internal fun blurredCoverRequestSize(useNativeBlur: Boolean): Int = if (useNativeBlur) 256 else 32

internal fun blurredCoverOverlayColors(): List<Color> = listOf(
    Color.Black.copy(alpha = 0.15f),
    Color.Black.copy(alpha = 0.40f),
    Color.Black.copy(alpha = 0.65f)
)

internal fun blurredCoverTintColors(dominantColor: Color): List<Color> = listOf(
    dominantColor.copy(alpha = 0.34f),
    dominantColor.copy(alpha = 0.12f),
    Color.Transparent
)
