package com.music.myapplication.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.feature.components.QualitySelector
import com.music.myapplication.feature.components.formatDuration

@Composable
fun PlayerControlsSection(
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onQualityChange: (String) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    useLightContent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val track = staticState.currentTrack ?: return
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val progressFraction = if (progress.durationMs > 0) {
        if (sliderDragging) sliderPosition else progress.positionMs.toFloat() / progress.durationMs
    } else {
        0f
    }

    val contentColor = if (useLightContent) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleColor = if (useLightContent) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    val activeAccent = if (useLightContent) accentColor else MaterialTheme.colorScheme.primary

    Column(modifier = modifier) {
        QualitySelector(
            currentQuality = staticState.quality,
            onQualitySelected = onQualityChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Canvas-drawn slider
        val density = LocalDensity.current
        val trackHeightPx = with(density) { 4.dp.toPx() }
        val thumbRadius by animateDpAsState(
            targetValue = if (sliderDragging) 8.dp else 6.dp,
            animationSpec = spring(),
            label = "thumbRadius"
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        sliderPosition = fraction
                        onSeek((fraction * progress.durationMs).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            sliderDragging = true
                            sliderPosition = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            sliderDragging = false
                            onSeek((sliderPosition * progress.durationMs).toLong())
                        },
                        onDragCancel = {
                            sliderDragging = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            sliderPosition = (sliderPosition + dragAmount / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val centerY = size.height / 2f
            val trackCornerRadius = CornerRadius(trackHeightPx / 2f)
            val currentFraction = progressFraction.coerceIn(0f, 1f)
            val thumbRadiusPx = thumbRadius.toPx()

            // Inactive track (full width capsule)
            drawRoundRect(
                color = contentColor.copy(alpha = 0.15f),
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = Size(canvasWidth, trackHeightPx),
                cornerRadius = trackCornerRadius
            )

            // Active track
            if (currentFraction > 0f) {
                drawRoundRect(
                    color = activeAccent,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(canvasWidth * currentFraction, trackHeightPx),
                    cornerRadius = trackCornerRadius
                )
            }

            // Thumb circle
            val thumbX = canvasWidth * currentFraction
            // Shadow
            if (sliderDragging) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.15f),
                    radius = thumbRadiusPx + 2.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
            drawCircle(
                color = activeAccent,
                radius = thumbRadiusPx,
                center = Offset(thumbX, centerY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(
                    if (sliderDragging) (sliderPosition * progress.durationMs).toLong() else progress.positionMs
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = subtleColor
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = subtleColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleMode) {
                Icon(
                    imageVector = when (staticState.playbackMode) {
                        PlaybackMode.SEQUENTIAL -> Icons.Default.Repeat
                        PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                        PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                    },
                    contentDescription = "播放模式",
                    modifier = Modifier.size(24.dp),
                    tint = subtleColor
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(36.dp),
                    tint = contentColor
                )
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(activeAccent)
            ) {
                Icon(
                    imageVector = if (staticState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (staticState.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(36.dp),
                    tint = contentColor
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    modifier = Modifier.size(24.dp),
                    tint = if (track.isFavorite) activeAccent else subtleColor
                )
            }
        }
    }
}
