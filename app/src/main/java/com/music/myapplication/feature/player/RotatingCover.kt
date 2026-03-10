package com.music.myapplication.feature.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.music.myapplication.feature.components.CoverImage

@Composable
fun RotatingCover(
    coverUrl: String,
    isPlaying: Boolean,
    glowColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    val coverShape = RoundedCornerShape(24.dp)

    // Breathing scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    val breathingGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingGlow"
    )

    val scale = if (isPlaying) breathingScale else 1.0f
    val glowAlpha = if (isPlaying) breathingGlowAlpha else 0.35f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow effect behind cover
        if (glowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.15f
                        scaleY = 1.15f
                        alpha = glowAlpha
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .drawBehind {
                        drawRect(color = glowColor.copy(alpha = 0.45f))
                    }
            )
        }

        CoverImage(
            url = coverUrl,
            contentDescription = "专辑封面",
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(coverShape),
            contentScale = ContentScale.Crop
        )
    }
}
