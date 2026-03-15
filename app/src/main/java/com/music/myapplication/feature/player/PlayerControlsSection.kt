package com.music.myapplication.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.feature.components.QualitySelector
import com.music.myapplication.feature.components.formatDuration
import com.music.myapplication.ui.theme.AppSpacing

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
    var playPulseTrigger by remember { mutableStateOf(0) }
    val trackKey = "${track.platform.id}:${track.id}"
    val favoritePopTrigger = rememberRisingEdgeTrigger(value = track.isFavorite, resetKey = trackKey)
    val modeRotation = rememberDelightSpinRotation(key = staticState.playbackMode)

    val progressFraction = if (progress.durationMs > 0) {
        if (sliderDragging) sliderPosition else progress.positionMs.toFloat() / progress.durationMs
    } else {
        0f
    }

    val contentColor = if (useLightContent) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleColor = if (useLightContent) Color.White.copy(alpha = 0.64f) else MaterialTheme.colorScheme.onSurfaceVariant
    val activeAccent = if (useLightContent) accentColor else MaterialTheme.colorScheme.primary
    val toolBackground = if (useLightContent) {
        Color.White.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val toolBorder = if (useLightContent) {
        Color.White.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QualitySelector(
                currentQuality = staticState.quality,
                onQualitySelected = onQualityChange
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerUtilityButton(
                    onClick = onToggleMode,
                    backgroundColor = toolBackground,
                    borderColor = toolBorder
                ) {
                    Icon(
                        imageVector = when (staticState.playbackMode) {
                            PlaybackMode.SEQUENTIAL -> Icons.Default.Repeat
                            PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                            PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                        },
                        contentDescription = "播放模式",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = modeRotation },
                        tint = subtleColor
                    )
                }

                PlayerUtilityButton(
                    onClick = onToggleFavorite,
                    backgroundColor = toolBackground,
                    borderColor = toolBorder,
                    hapticFeedbackType = HapticFeedbackType.LongPress
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        DelightIconPopOverlay(
                            trigger = favoritePopTrigger,
                            imageVector = Icons.Default.Favorite,
                            tint = activeAccent,
                            size = 22.dp
                        )
                        Crossfade(
                            targetState = track.isFavorite,
                            label = "favoriteCrossfade"
                        ) { isFavorite ->
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                                modifier = Modifier.size(22.dp),
                                tint = if (isFavorite) activeAccent else subtleColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Medium))

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

            drawRoundRect(
                color = contentColor.copy(alpha = 0.15f),
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = Size(canvasWidth, trackHeightPx),
                cornerRadius = trackCornerRadius
            )

            if (currentFraction > 0f) {
                drawRoundRect(
                    color = activeAccent,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(canvasWidth * currentFraction, trackHeightPx),
                    cornerRadius = trackCornerRadius
                )
            }

            val thumbX = canvasWidth * currentFraction
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
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = subtleColor
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = subtleColor
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.XLarge))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerTransportButton(
                    onClick = onPrevious,
                    size = 52.dp,
                    backgroundColor = toolBackground,
                    borderColor = toolBorder
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(28.dp),
                        tint = contentColor
                    )
                }

                PlayerTransportButton(
                    onClick = {
                        playPulseTrigger += 1
                        onPlayPause()
                    },
                    size = 72.dp,
                    backgroundColor = activeAccent,
                    borderColor = Color.Transparent,
                    hapticFeedbackType = HapticFeedbackType.LongPress,
                    pressedScale = 0.92f
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        DelightPulseOverlay(
                            trigger = playPulseTrigger,
                            color = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                        Crossfade(
                            targetState = staticState.isPlaying,
                            label = "playPauseCrossfade"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(34.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                PlayerTransportButton(
                    onClick = onNext,
                    size = 52.dp,
                    backgroundColor = toolBackground,
                    borderColor = toolBorder
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(28.dp),
                        tint = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerUtilityButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    borderColor: Color,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    pressedScale: Float = 0.94f,
    content: @Composable () -> Unit
) {
    DelightIconButton(
        onClick = onClick,
        pressedScale = pressedScale,
        hapticFeedbackType = hapticFeedbackType,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
    ) {
        content()
    }
}

@Composable
private fun PlayerTransportButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    borderColor: Color,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    pressedScale: Float = 0.94f,
    content: @Composable () -> Unit
) {
    DelightIconButton(
        onClick = onClick,
        pressedScale = pressedScale,
        hapticFeedbackType = hapticFeedbackType,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
    ) {
        content()
    }
}
