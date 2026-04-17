package com.music.myapplication.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.feature.components.formatDuration

@Composable
internal fun SecondaryActionRow(
    trackKey: String,
    isFavorite: Boolean,
    onOpenVideo: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onShowComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favoritePopTrigger = rememberRisingEdgeTrigger(value = isFavorite, resetKey = trackKey)
    val buttonSize = 36.dp
    val iconSize = 16.dp
    val buttonSpacing = 14.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryActionButton(
            onClick = onOpenVideo ?: {},
            icon = Icons.Default.OndemandVideo,
            contentDescription = "视频",
            enabled = onOpenVideo != null,
            buttonSize = buttonSize,
            iconSize = iconSize
        )

        Spacer(modifier = Modifier.width(buttonSpacing))

        SecondaryActionButton(
            onClick = onToggleFavorite,
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "收藏",
            tint = if (isFavorite) Color(0xFFE53935) else Color.White.copy(alpha = 0.72f),
            hapticFeedbackType = HapticFeedbackType.LongPress,
            overlay = {
                DelightIconPopOverlay(
                    trigger = favoritePopTrigger,
                    imageVector = Icons.Default.Favorite,
                    tint = Color(0xFFE53935),
                    size = iconSize
                )
            },
            buttonSize = buttonSize,
            iconSize = iconSize
        )

        Spacer(modifier = Modifier.width(buttonSpacing))

        SecondaryActionButton(
            onClick = onShowComments,
            icon = Icons.Default.Forum,
            contentDescription = "评论",
            buttonSize = buttonSize,
            iconSize = iconSize
        )
    }
}

@Composable
private fun SecondaryActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.72f),
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    overlay: @Composable (() -> Unit)? = null,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 16.dp
) {
    val backgroundAlpha = if (enabled) 0.12f else 0.08f
    val iconTint = if (enabled) tint else tint.copy(alpha = 0.42f)

    DelightIconButton(
        onClick = onClick,
        enabled = enabled,
        hapticFeedbackType = hapticFeedbackType,
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
            overlay?.invoke()
        }
    }
}

@Composable
internal fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val animatedWidth by animateDpAsState(
                targetValue = if (index == currentPage) 20.dp else 6.dp,
                label = "indicatorWidth_$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(animatedWidth)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (index == currentPage) Color.White
                        else Color.White.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
internal fun PlayerProgressSection(
    progress: PlaybackProgressUiState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val progressFraction = if (progress.durationMs > 0) {
        if (sliderDragging) sliderPosition else progress.positionMs.toFloat() / progress.durationMs
    } else {
        0f
    }

    Column(modifier = modifier) {
        Slider(
            value = progressFraction.coerceIn(0f, 1f),
            onValueChange = {
                sliderDragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                sliderDragging = false
                onSeek((sliderPosition * progress.durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(
                    if (sliderDragging) (sliderPosition * progress.durationMs).toLong() else progress.positionMs
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
internal fun TransportControlRow(
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    onToggleMode: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playPulseTrigger by remember { mutableStateOf(0) }
    val modeRotation = rememberDelightSpinRotation(key = playbackMode)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportEdgeControl(
            icon = playbackMode.icon(),
            contentDescription = "播放模式",
            onClick = onToggleMode,
            rotationZ = modeRotation
        )
        DelightIconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
        DelightIconButton(
            onClick = {
                playPulseTrigger += 1
                onPlayPause()
            },
            pressedScale = 0.92f,
            hapticFeedbackType = HapticFeedbackType.LongPress,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
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
                    targetState = isPlaying,
                    label = "lyricsPlayPauseCrossfade"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
            }
        }
        DelightIconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
        TransportEdgeControl(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = "播放队列",
            onClick = onOpenQueue
        )
    }
}

@Composable
private fun TransportEdgeControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotationZ: Float = 0f,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove
) {
    DelightIconButton(
        onClick = onClick,
        hapticFeedbackType = hapticFeedbackType,
        modifier = modifier.size(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { this.rotationZ = rotationZ },
            tint = Color.White.copy(alpha = 0.9f)
        )
    }
}

private fun PlaybackMode.icon(): ImageVector = when (this) {
    PlaybackMode.SEQUENTIAL -> Icons.Default.Repeat
    PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
    PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
}
