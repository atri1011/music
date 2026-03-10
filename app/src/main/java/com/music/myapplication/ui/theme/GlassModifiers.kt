package com.music.myapplication.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism surface: semi-transparent background + border + optional blur.
 * When [pressScale] is true, adds a press-to-shrink animation (0.97f scale with spring rebound).
 */
fun Modifier.glassSurface(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    pressScale: Boolean = false
): Modifier = composed {
    val glassColors = LocalGlassColors.current
    val pressModifier = if (pressScale) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "glassPressScale"
        )
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    } else {
        Modifier
    }
    this
        .then(pressModifier)
        .clip(shape)
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
