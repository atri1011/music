package com.music.myapplication.feature.player

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalSharedTransitionApi::class)
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
    val miniProgress = calculateMiniPlayerProgress(
        positionMs = progress.positionMs,
        durationMs = progress.durationMs
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // MiniPlayerBar: visible when sheet is mostly collapsed
            if (shouldShowMiniPlayer(sheetFraction)) {
                MiniPlayerBar(
                    track = staticState.currentTrack,
                    isPlaying = staticState.isPlaying,
                    quality = staticState.quality,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onClick = onExpandClick,
                    progressFraction = miniProgress,
                    modifier = Modifier.alpha(miniPlayerAlpha(sheetFraction))
                )
            }

            // FullScreenPlayer: visible when sheet is expanding
            if (shouldShowFullScreenPlayer(sheetFraction)) {
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
                    modifier = Modifier.alpha(fullScreenPlayerAlpha(sheetFraction))
                )
            }
        }
    }
}
