package com.music.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies blur on API 31+ (Android 12), no-op on lower versions.
 */
fun Modifier.adaptiveBlur(radius: Dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius)
    } else {
        this
    }
}

/**
 * Glassmorphism surface: semi-transparent background + border + optional blur.
 */
@Composable
fun Modifier.glassSurface(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 24.dp,
    isDark: Boolean = isSystemInDarkTheme()
): Modifier {
    val glassColors = LocalGlassColors.current
    return this
        .clip(shape)
        .adaptiveBlur(blurRadius)
        .background(glassColors.surface, shape)
        .border(0.5.dp, glassColors.border, shape)
}

/**
 * Gradient scrim overlay for text readability on images.
 */
fun Modifier.verticalGradientScrim(
    color: Color,
    startY: Float = 0f,
    endY: Float = 1f
): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color),
            startY = size.height * startY,
            endY = size.height * endY
        )
    )
}

/**
 * Radial gradient background from a dominant color — used for player background.
 */
fun Modifier.playerGradientBackground(
    dominantColor: Color,
    baseColor: Color
): Modifier = drawBehind {
    drawRect(color = baseColor)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                dominantColor.copy(alpha = 0.5f),
                dominantColor.copy(alpha = 0.2f),
                Color.Transparent
            ),
            center = Offset(size.width * 0.5f, size.height * 0.25f),
            radius = size.maxDimension * 0.8f
        )
    )
}
