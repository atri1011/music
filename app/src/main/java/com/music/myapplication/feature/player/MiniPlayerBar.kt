package com.music.myapplication.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.glassSurface

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    track: Track?,
    isPlaying: Boolean,
    quality: String,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit,
    onSwipeExpand: () -> Unit = onClick,
    onLongPress: () -> Unit = {},
    showResolvingIndicator: Boolean = false,
    progressFraction: Float = 0f,
    sharedTransitionScope: SharedTransitionScope? = null,
    sharedArtworkVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentTrack = track ?: return
    val haptics = LocalHapticFeedback.current
    var playPulseTrigger by remember { mutableStateOf(0) }
    val trackKey = "${currentTrack.platform.id}:${currentTrack.id}"
    val favoritePopTrigger = rememberRisingEdgeTrigger(value = currentTrack.isFavorite, resetKey = trackKey)
    val shape = RoundedCornerShape(AppShapes.XLarge)
    val progress = progressFraction.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .miniPlayerGestureLayer(
                onSwipeUp = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSwipeExpand()
                },
                onSwipePrevious = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevious()
                },
                onSwipeNext = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNext()
                }
            )
            .glassSurface(shape = shape)
    ) {
        if (showResolvingIndicator) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = AppShapes.XLarge,
                            topEnd = AppShapes.XLarge
                        )
                    ),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .clip(RoundedCornerShape(topEnd = AppShapes.Tiny, bottomEnd = AppShapes.Tiny))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Small, vertical = AppSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(AppShapes.Large))
                    .pointerInput(trackKey, onClick, onLongPress) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress()
                            }
                        )
                    }
                    .padding(end = AppSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(AppShapes.Small))
                ) {
                    CoverImage(
                        url = currentTrack.coverUrl,
                        contentDescription = currentTrack.title,
                        modifier = Modifier
                            .sharedTrackArtwork(
                                sharedTransitionScope = sharedTransitionScope,
                                track = currentTrack,
                                visible = sharedArtworkVisible
                            )
                            .fillMaxSize()
                    )
                    if (currentTrack.isFavorite) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(topStart = AppShapes.ExtraSmall))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "已收藏",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(AppSpacing.Small))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.XXSmall))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.XXSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPremiumAudioQuality(quality)) {
                            AudioQualityBadge(
                                quality = quality,
                                platform = currentTrack.platform,
                                compact = true
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DelightIconButton(
                    onClick = onToggleFavorite,
                    hapticFeedbackType = HapticFeedbackType.LongPress,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        DelightIconPopOverlay(
                            trigger = favoritePopTrigger,
                            imageVector = Icons.Default.Favorite,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 20.dp
                        )
                        Crossfade(
                            targetState = currentTrack.isFavorite,
                            label = "miniFavoriteCrossfade"
                        ) { isFavorite ->
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                DelightIconButton(
                    onClick = {
                        playPulseTrigger += 1
                        onPlayPause()
                    },
                    pressedScale = 0.92f,
                    hapticFeedbackType = HapticFeedbackType.LongPress,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f))
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
                            label = "miniPlayPauseCrossfade"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "暂停" else "播放",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                DelightIconButton(
                    onClick = onNext,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun Modifier.miniPlayerGestureLayer(
    onSwipeUp: () -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
): Modifier = pointerInput(onSwipeUp, onSwipePrevious, onSwipeNext) {
    var totalDragX = 0f
    var totalDragY = 0f
    detectDragGestures(
        onDragStart = {
            totalDragX = 0f
            totalDragY = 0f
        },
        onDrag = { change, dragAmount ->
            totalDragX += dragAmount.x
            totalDragY += dragAmount.y
            if (kotlin.math.abs(totalDragX) > 10f || kotlin.math.abs(totalDragY) > 10f) {
                change.consume()
            }
        },
        onDragEnd = {
            val horizontalThreshold = size.width * 0.22f
            val verticalThreshold = size.height * 0.46f
            val absX = kotlin.math.abs(totalDragX)
            val absY = kotlin.math.abs(totalDragY)
            when {
                totalDragY < -verticalThreshold && absY > absX -> onSwipeUp()
                totalDragX > horizontalThreshold && absX > absY -> onSwipePrevious()
                totalDragX < -horizontalThreshold && absX > absY -> onSwipeNext()
            }
            totalDragX = 0f
            totalDragY = 0f
        },
        onDragCancel = {
            totalDragX = 0f
            totalDragY = 0f
        }
    )
}
