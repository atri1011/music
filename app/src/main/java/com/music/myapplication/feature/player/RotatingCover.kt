package com.music.myapplication.feature.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.ui.theme.AppShapes

@Composable
fun RotatingCover(
    coverUrl: String,
    isPlaying: Boolean,
    glowColor: Color = Color.Transparent,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coverShape = RoundedCornerShape(AppShapes.Large)
    val haptics = LocalHapticFeedback.current

    // Breathing scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = ROTATING_COVER_IDLE_SCALE,
        targetValue = ROTATING_COVER_ACTIVE_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(ROTATING_COVER_BREATHING_DURATION_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    val breathingGlowAlpha by infiniteTransition.animateFloat(
        initialValue = ROTATING_COVER_IDLE_GLOW_ALPHA,
        targetValue = ROTATING_COVER_ACTIVE_GLOW_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(ROTATING_COVER_BREATHING_DURATION_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingGlow"
    )

    val scale = rotatingCoverScale(isPlaying, breathingScale)
    val glowAlpha = rotatingCoverGlowAlpha(isPlaying, breathingGlowAlpha)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow effect behind cover
        if (shouldShowRotatingCoverGlow(glowColor)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = ROTATING_COVER_GLOW_SCALE
                        scaleY = ROTATING_COVER_GLOW_SCALE
                        alpha = glowAlpha
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .drawBehind {
                        drawRect(color = rotatingCoverGlowDrawColor(glowColor))
                    }
            )
        }

        CoverImage(
            url = coverUrl,
            contentDescription = "专辑封面",
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (onLongPress != null) {
                        Modifier.pointerInput(onLongPress) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(coverShape),
            contentScale = ContentScale.Crop
        )
    }
}
