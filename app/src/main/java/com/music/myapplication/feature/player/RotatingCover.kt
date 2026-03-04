package com.music.myapplication.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun RotatingCover(
    coverUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var pausedAngle by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "cover_rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val displayAngle = if (isPlaying) {
        pausedAngle = angle
        angle
    } else {
        pausedAngle
    }

    AsyncImage(
        model = coverUrl,
        contentDescription = "专辑封面",
        modifier = modifier
            .size(280.dp)
            .clip(CircleShape)
            .graphicsLayer { rotationZ = displayAngle },
        contentScale = ContentScale.Crop
    )
}
