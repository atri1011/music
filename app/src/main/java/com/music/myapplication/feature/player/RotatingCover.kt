package com.music.myapplication.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
                .clip(coverShape),
            contentScale = ContentScale.Crop
        )
    }
}
