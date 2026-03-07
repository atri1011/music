package com.music.myapplication.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.music.myapplication.feature.components.CoverImage
import kotlinx.coroutines.isActive

@Composable
fun RotatingCover(
    coverUrl: String,
    isPlaying: Boolean,
    glowColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

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
                        alpha = if (isPlaying) 0.6f else 0.25f
                    }
                    .drawBehind {
                        drawCircle(
                            color = glowColor.copy(alpha = 0.45f),
                            radius = size.minDimension / 2f
                        )
                    }
            )
        }

        CoverImage(
            url = coverUrl,
            contentDescription = "专辑封面",
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .graphicsLayer { rotationZ = rotation.value },
            contentScale = ContentScale.Crop
        )
    }
}
