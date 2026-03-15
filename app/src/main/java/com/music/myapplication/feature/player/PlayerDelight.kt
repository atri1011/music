package com.music.myapplication.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
internal fun DelightIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "delightPressScale"
    )

    IconButton(
        onClick = {
            if (hapticFeedbackType != null) {
                haptics.performHapticFeedback(hapticFeedbackType)
            }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        content()
    }
}

@Composable
internal fun rememberDelightSpinRotation(
    key: Any?,
    degrees: Float = 240f,
    durationMillis: Int = 420
): Float {
    val anim = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }
    return anim.value * degrees
}

@Composable
internal fun rememberRisingEdgeTrigger(
    value: Boolean,
    resetKey: Any?
): Int {
    var last by remember(resetKey) { mutableStateOf(value) }
    var trigger by remember(resetKey) { mutableStateOf(0) }
    LaunchedEffect(resetKey, value) {
        if (value && !last) {
            trigger += 1
        }
        last = value
    }
    return trigger
}

@Composable
internal fun DelightPulseOverlay(
    trigger: Int,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
    durationMillis: Int = 360
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val progress = anim.value
        if (progress <= 0f || progress >= 1f) return@Canvas

        val baseRadius = min(size.width, size.height) / 2f
        val radius = baseRadius * (0.65f + 0.35f * progress)
        val alpha = (1f - progress).coerceIn(0f, 1f)

        val fillAlpha = 0.10f * alpha
        if (fillAlpha > 0f) {
            drawCircle(
                color = color.copy(alpha = fillAlpha),
                radius = radius
            )
        }
        drawCircle(
            color = color.copy(alpha = 0.35f * alpha),
            radius = radius,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

@Composable
internal fun DelightIconPopOverlay(
    trigger: Int,
    imageVector: ImageVector,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    durationMillis: Int = 520
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }

    val progress = anim.value
    if (progress <= 0f || progress >= 1f) return

    val alpha = (1f - progress).coerceIn(0f, 1f)
    val scale = if (progress < 0.5f) {
        0.6f + (progress / 0.5f) * 0.6f
    } else {
        1.2f - ((progress - 0.5f) / 0.5f) * 0.2f
    }

    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint.copy(alpha = alpha),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = progress * 18f
            }
    )
}
