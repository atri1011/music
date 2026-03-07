package com.music.myapplication.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

@Composable
fun PlayerBottomSheet(
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    sheetFraction: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onQualityChange: (String) -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val miniProgress = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // MiniPlayerBar: visible when sheet is mostly collapsed
            if (sheetFraction < 0.5f) {
                MiniPlayerBar(
                    track = staticState.currentTrack,
                    isPlaying = staticState.isPlaying,
                    quality = staticState.quality,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onClick = onExpandClick,
                    progressFraction = miniProgress,
                    modifier = Modifier.alpha(1f - sheetFraction * 2)
                )
            }

            // FullScreenPlayer: visible when sheet is expanding
            if (sheetFraction > 0.0f) {
                FullScreenPlayer(
                    staticState = staticState,
                    progress = progress,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleMode = onToggleMode,
                    onToggleFavorite = onToggleFavorite,
                    onQualityChange = onQualityChange,
                    modifier = Modifier.alpha((sheetFraction * 2).coerceAtMost(1f))
                )
            }
        }
    }
}
