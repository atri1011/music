package com.music.myapplication.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun MiniPlayerBar(
    track: Track?,
    isPlaying: Boolean,
    quality: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit,
    showResolvingIndicator: Boolean = false,
    progressFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val currentTrack = track ?: return
    var playPulseTrigger by remember { mutableStateOf(0) }
    val trackKey = "${currentTrack.platform.id}:${currentTrack.id}"
    val favoritePopTrigger = rememberRisingEdgeTrigger(value = currentTrack.isFavorite, resetKey = trackKey)
    val shape = RoundedCornerShape(AppShapes.Large)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = shape)
    ) {
        if (showResolvingIndicator) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = AppShapes.Large,
                            topEnd = AppShapes.Large
                        )
                    ),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Small, vertical = AppSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(AppShapes.Medium))
                    .clickable(onClick = onClick)
                    .padding(end = AppSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    url = currentTrack.coverUrl,
                    contentDescription = currentTrack.title,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(AppShapes.Small))
                )

                Spacer(modifier = Modifier.width(AppSpacing.Small))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
                        if (isVipTrack(quality)) {
                            MiniQualityBadge(quality = quality)
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
                        .background(MaterialTheme.colorScheme.primary)
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
                                tint = Color.White,
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

        val progress = progressFraction
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = AppShapes.Large,
                            bottomEnd = AppShapes.Large
                        )
                    ),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun MiniQualityBadge(
    quality: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = quality.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(AppShapes.Small))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = AppSpacing.XXSmall, vertical = AppSpacing.XXSmall)
    )
}

private fun isVipTrack(trackQuality: String): Boolean {
    return trackQuality.equals("320k", ignoreCase = true) ||
        trackQuality.equals("flac", ignoreCase = true) ||
        trackQuality.equals("flac24bit", ignoreCase = true)
}
