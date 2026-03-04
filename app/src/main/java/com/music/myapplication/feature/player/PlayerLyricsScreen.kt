package com.music.myapplication.feature.player

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Track

@Composable
fun PlayerLyricsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by playerViewModel.playbackState.collectAsState()
    val lyricsState by playerViewModel.lyricsUiState.collectAsState()
    val currentTrack = playbackState.currentTrack

    LaunchedEffect(Unit) {
        playerViewModel.showLyricsPanel()
    }

    LaunchedEffect(currentTrack?.id, currentTrack?.platform?.id) {
        if (currentTrack == null) {
            onBack()
        }
    }

    if (currentTrack == null) return

    val currentLyricIndex by remember(lyricsState.lyrics, playbackState.positionMs) {
        derivedStateOf {
            LyricsParser.findCurrentIndex(lyricsState.lyrics, playbackState.positionMs)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = currentTrack.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        LyricsModeToggle(
            currentMode = lyricsState.viewMode,
            onModeChange = playerViewModel::setLyricsPanelMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (lyricsState.viewMode) {
                LyricsPanelMode.LYRICS -> LyricsPanelContent(
                    lyricsState = lyricsState,
                    currentIndex = currentLyricIndex,
                    modifier = Modifier.fillMaxSize()
                )

                LyricsPanelMode.COVER -> CoverPanel(
                    state = playbackState,
                    track = currentTrack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PlayerControlsSection(
            state = playbackState,
            onPlayPause = playerViewModel::togglePlayPause,
            onNext = playerViewModel::skipNext,
            onPrevious = playerViewModel::skipPrevious,
            onSeek = playerViewModel::seekTo,
            onToggleMode = playerViewModel::togglePlaybackMode,
            onToggleFavorite = playerViewModel::toggleFavorite,
            onQualityChange = playerViewModel::setQuality,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LyricsModeToggle(
    currentMode: LyricsPanelMode,
    onModeChange: (LyricsPanelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentMode == LyricsPanelMode.LYRICS) {
            FilledTonalButton(onClick = {}) { Text("歌词") }
        } else {
            OutlinedButton(onClick = { onModeChange(LyricsPanelMode.LYRICS) }) { Text("歌词") }
        }

        if (currentMode == LyricsPanelMode.COVER) {
            FilledTonalButton(onClick = {}) { Text("封面") }
        } else {
            OutlinedButton(onClick = { onModeChange(LyricsPanelMode.COVER) }) { Text("封面") }
        }
    }
}

@Composable
private fun LyricsPanelContent(
    lyricsState: LyricsUiState,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    when {
        lyricsState.isLoading && lyricsState.lyrics.isEmpty() -> StatusHint(
            text = "歌词加载中...",
            modifier = modifier
        )

        !lyricsState.errorMessage.isNullOrBlank() && lyricsState.lyrics.isEmpty() -> StatusHint(
            text = lyricsState.errorMessage,
            modifier = modifier
        )

        else -> LyricsView(
            lyrics = lyricsState.lyrics,
            currentIndex = currentIndex,
            modifier = modifier
        )
    }
}

@Composable
private fun CoverPanel(
    state: PlaybackState,
    track: Track,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RotatingCover(
            coverUrl = track.coverUrl,
            isPlaying = state.isPlaying,
            modifier = Modifier.size(260.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusHint(text: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.orEmpty().ifBlank { "暂无歌词" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
