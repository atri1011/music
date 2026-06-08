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

    val progressFraction = playerControlsProgressFraction(
        durationMs = progress.durationMs,
        positionMs = progress.positionMs,
        sliderDragging = sliderDragging,
        sliderPosition = sliderPosition
    )

    val colors = playerControlsPalette(
        useLightContent = useLightContent,
        accentColor = accentColor,
        colorScheme = MaterialTheme.colorScheme
    )
    val contentColor = colors.contentColor
    val subtleColor = colors.subtleColor
    val activeAccent = colors.activeAccent
    val toolBackground = colors.toolBackground
    val toolBorder = colors.toolBorder

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QualitySelector(
                    currentQuality = staticState.quality,
                    onQualitySelected = onQualityChange
                )
                AudioQualityBadge(
                    quality = staticState.quality,
                    platform = track.platform
                )
            }

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
                        imageVector = playerControlsModeIcon(staticState.playbackMode),
                        contentDescription = "播放模式",
                        modifier = Modifier
                            .size(playerControlsUtilityIconSize())
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
                            size = playerControlsUtilityIconSize()
                        )
                        Crossfade(
                            targetState = track.isFavorite,
                            label = "favoriteCrossfade"
                        ) { isFavorite ->
                            Icon(
                                imageVector = playerControlsFavoriteIcon(isFavorite),
                                contentDescription = "收藏",
                                modifier = Modifier.size(playerControlsUtilityIconSize()),
                                tint = playerControlsFavoriteTint(
                                    isFavorite = isFavorite,
                                    activeAccent = activeAccent,
                                    subtleColor = subtleColor
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Medium))

        val density = LocalDensity.current
        val trackHeight = playerControlsTrackHeight()
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val thumbRadius by animateDpAsState(
            targetValue = playerControlsThumbRadius(sliderDragging),
            animationSpec = spring(),
            label = "thumbRadius"
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = playerControlsFractionFromOffset(
                            offsetX = offset.x,
                            width = size.width.toFloat()
                        )
                        sliderPosition = fraction
                        onSeek(playerControlsSeekPositionMs(progress.durationMs, fraction))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            sliderDragging = true
                            sliderPosition = playerControlsFractionFromOffset(
                                offsetX = offset.x,
                                width = size.width.toFloat()
                            )
                        },
                        onDragEnd = {
                            sliderDragging = false
                            onSeek(playerControlsSeekPositionMs(progress.durationMs, sliderPosition))
                        },
                        onDragCancel = {
                            sliderDragging = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            sliderPosition = playerControlsDraggedSliderPosition(
                                sliderPosition = sliderPosition,
                                dragAmount = dragAmount,
                                width = size.width.toFloat()
                            )
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
                    color = Color.Black.copy(alpha = playerControlsThumbHaloAlpha()),
                    radius = playerControlsThumbHaloRadius(thumbRadius).toPx(),
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
                    playerControlsDisplayedPositionMs(
                        durationMs = progress.durationMs,
                        positionMs = progress.positionMs,
                        sliderDragging = sliderDragging,
                        sliderPosition = sliderPosition
                    )
                ),
                style = playerControlsDurationTextStyle(MaterialTheme.typography),
                color = subtleColor
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = playerControlsDurationTextStyle(MaterialTheme.typography),
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
                    size = playerControlsTransportButtonSize(primary = false),
                    backgroundColor = toolBackground,
                    borderColor = toolBorder
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(playerControlsTransportIconSize(primary = false)),
                        tint = contentColor
                    )
                }

                PlayerTransportButton(
                    onClick = {
                        playPulseTrigger += 1
                        onPlayPause()
                    },
                    size = playerControlsTransportButtonSize(primary = true),
                    backgroundColor = activeAccent,
                    borderColor = Color.Transparent,
                    hapticFeedbackType = HapticFeedbackType.LongPress,
                    pressedScale = playerControlsPrimaryPressedScale()
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
                                imageVector = playerControlsPlayPauseIcon(isPlaying),
                                contentDescription = playerControlsPlayPauseContentDescription(isPlaying),
                                modifier = Modifier.size(playerControlsTransportIconSize(primary = true)),
                                tint = Color.White
                            )
                        }
                    }
                }

                PlayerTransportButton(
                    onClick = onNext,
                    size = playerControlsTransportButtonSize(primary = false),
                    backgroundColor = toolBackground,
                    borderColor = toolBorder
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(playerControlsTransportIconSize(primary = false)),
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
    pressedScale: Float = playerControlsDefaultPressedScale(),
    content: @Composable () -> Unit
) {
    DelightIconButton(
        onClick = onClick,
        pressedScale = pressedScale,
        hapticFeedbackType = hapticFeedbackType,
        modifier = Modifier
            .size(playerControlsUtilityButtonSize())
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
    pressedScale: Float = playerControlsDefaultPressedScale(),
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
