package com.music.myapplication.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint as AndroidPaint
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Glassmorphism surface: semi-transparent background + border + optional blur.
 * When [pressScale] is true, adds a press-to-shrink animation (0.97f scale with spring rebound).
 */
fun Modifier.glassSurface(
    shape: RoundedCornerShape = RoundedCornerShape(AppShapes.Medium),
    pressScale: Boolean = false
): Modifier = composed {
    val glassColors = LocalGlassColors.current
    val pressModifier = if (pressScale) {
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "glassPressScale"
        )
        Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
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

/**
 * Modern premium app background: tinted aurora gradients + subtle grain texture.
 *
 * Designed for "质感/现代/优美" without heavy blur, neon, or noisy patterns.
 */
fun Modifier.appPremiumBackground(
    primary: Color = MaterialTheme.colorScheme.primary,
    secondary: Color = MaterialTheme.colorScheme.secondary,
    base: Color = MaterialTheme.colorScheme.background,
    grainAlpha: Float = 0.035f
): Modifier = composed {
    val isDark = base.luminance() < 0.5f
    val tunedGrainAlpha = (if (isDark) grainAlpha * 0.55f else grainAlpha).coerceIn(0f, 0.12f)

    val topLeft = primary.copy(alpha = if (isDark) 0.40f else 0.18f)
    val topRight = secondary.copy(alpha = if (isDark) 0.22f else 0.12f)
    val bottomFade = base.copy(alpha = if (isDark) 0.96f else 0.92f)

    this.drawWithCache {
        val grainPaint = if (tunedGrainAlpha > 0f) {
            val grain = createGrainBitmap(sizePx = 96, seed = 7)
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = (tunedGrainAlpha * 255f).roundToInt().coerceIn(0, 255)
            }
        } else {
            null
        }

        onDrawBehind {
            drawRect(color = base)

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(topLeft, Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.08f),
                    radius = size.maxDimension * 0.95f
                )
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(topRight, Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.10f),
                    radius = size.maxDimension * 0.90f
                )
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, bottomFade, base),
                    startY = 0f,
                    endY = size.height
                )
            )

            if (grainPaint != null) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, grainPaint)
                }
            }
        }
    }
}

private fun createGrainBitmap(
    sizePx: Int,
    seed: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(sizePx * sizePx)
    val random = Random(seed)

    // Subtle neutral grain (small variance around mid-gray). Avoid high contrast noise.
    for (i in pixels.indices) {
        val v = 128 + random.nextInt(from = -24, until = 25)
        val c = v.coerceIn(0, 255)
        pixels[i] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    }

    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap
}
