package com.music.myapplication.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        QualitySelector(
            currentQuality = staticState.quality,
            onQualitySelected = onQualityChange
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = if (staticState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (staticState.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    modifier = Modifier.size(24.dp),
                    tint = if (track.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
