package com.music.myapplication.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import com.music.myapplication.ui.theme.rememberDominantColorState

@Composable
fun PlayerLyricsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
    val progress by playerViewModel.progressState.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyricsUiState.collectAsStateWithLifecycle()
    val currentTrack = staticState.currentTrack
    var hasLoadedTrack by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playerViewModel.showLyricsPanel()
    }

    LaunchedEffect(currentTrack?.id, currentTrack?.platform?.id) {
        if (currentTrack != null) {
            hasLoadedTrack = true
        } else if (hasLoadedTrack) {
            onBack()
        }
    }

    if (currentTrack == null) return

    val currentLyricIndex = remember(lyricsState.lyrics, progress.positionMs) {
        LyricsParser.findCurrentIndex(lyricsState.lyrics, progress.positionMs)
    }

    val dominantColorState = rememberDominantColorState(coverUrl = currentTrack.coverUrl)

    val animatedDominant by animateColorAsState(
        targetValue = dominantColorState.dominantColor,
        animationSpec = tween(800),
        label = "dominantColor"
    )
    val animatedMuted by animateColorAsState(
        targetValue = dominantColorState.mutedColor,
        animationSpec = tween(800),
        label = "mutedColor"
    )

    val pageTopColor = Color(0xFFEDEFF2)
    val pageMiddleColor = Color(0xFFE8ECEF)
    val pageBottomColor = Color(0xFFE6EAEE)
    val titleColor = Color(0xFF31373D)
    val subtitleColor = Color(0xFF6A727A)
    val activeLyricColor = Color(0xFF161A1F)
    val inactiveLyricColor = Color(0xFF7A828B)
    val bottomTint by animateColorAsState(
        targetValue = lerp(animatedMuted, Color(0xFFEFB1BE), 0.65f),
        animationSpec = tween(800),
        label = "bottomTint"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(pageTopColor, pageMiddleColor, pageBottomColor)
                )
            )
    ) {
        // Light scrim to keep QQ-like lyric readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD9F5F7F8),
                            Color(0xE6EFF2F4),
                            Color(0xF2E9EDF1)
                        )
                    )
                )
        )

        // Bottom warm tint
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to bottomTint.copy(alpha = 0.28f)
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = subtitleColor
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Spacer for symmetry with back button
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Mode toggle
            LyricsModeToggle(
                currentMode = lyricsState.viewMode,
                onModeChange = playerViewModel::setLyricsPanelMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (lyricsState.viewMode) {
                    LyricsPanelMode.LYRICS -> LyricsPanelContent(
                        lyricsState = lyricsState,
                        currentIndex = currentLyricIndex,
                        activeLyricColor = activeLyricColor,
                        inactiveLyricColor = inactiveLyricColor,
                        scrimColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    )
                    LyricsPanelMode.COVER -> CoverPanel(
                        track = currentTrack,
                        isPlaying = staticState.isPlaying,
                        glowColor = animatedDominant,
                        lyrics = lyricsState.lyrics,
                        currentIndex = currentLyricIndex,
                        primaryLyricColor = activeLyricColor,
                        secondaryLyricColor = inactiveLyricColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls
            PlayerControlsSection(
                staticState = staticState,
                progress = progress,
                onPlayPause = playerViewModel::togglePlayPause,
                onNext = playerViewModel::skipNext,
                onPrevious = playerViewModel::skipPrevious,
                onSeek = playerViewModel::seekTo,
                onToggleMode = playerViewModel::togglePlaybackMode,
                onToggleFavorite = playerViewModel::toggleFavorite,
                onQualityChange = playerViewModel::setQuality,
                accentColor = animatedDominant,
                useLightContent = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
        val shape = RoundedCornerShape(20.dp)
        if (currentMode == LyricsPanelMode.LYRICS) {
            FilledTonalButton(
                onClick = {},
                shape = shape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.76f),
                    contentColor = Color(0xFF2F353B)
                )
            ) { Text("歌词") }
        } else {
            OutlinedButton(
                onClick = { onModeChange(LyricsPanelMode.LYRICS) },
                shape = shape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF626B74))
            ) { Text("歌词") }
        }

        if (currentMode == LyricsPanelMode.COVER) {
            FilledTonalButton(
                onClick = {},
                shape = shape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.76f),
                    contentColor = Color(0xFF2F353B)
                )
            ) { Text("封面") }
        } else {
            OutlinedButton(
                onClick = { onModeChange(LyricsPanelMode.COVER) },
                shape = shape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF626B74))
            ) { Text("封面") }
        }
    }
}

@Composable
private fun LyricsPanelContent(
    lyricsState: LyricsUiState,
    currentIndex: Int,
    activeLyricColor: Color,
    inactiveLyricColor: Color,
    scrimColor: Color,
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
            activeLineColor = activeLyricColor,
            inactiveLineColor = inactiveLyricColor,
            scrimColor = scrimColor,
            modifier = modifier
        )
    }
}

@Composable
private fun CoverPanel(
    track: Track,
    isPlaying: Boolean,
    glowColor: Color,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    primaryLyricColor: Color,
    secondaryLyricColor: Color,
    modifier: Modifier = Modifier
) {
    val previewLines = remember(lyrics, currentIndex, track.artist) {
        resolveCoverLyricsPreview(lyrics = lyrics, currentIndex = currentIndex, fallback = track.artist)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RotatingCover(
            coverUrl = track.coverUrl,
            isPlaying = isPlaying,
            glowColor = glowColor,
            modifier = Modifier.size(280.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = previewLines.currentLine,
            style = MaterialTheme.typography.titleLarge,
            color = primaryLyricColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = previewLines.nextLine,
            style = MaterialTheme.typography.bodyLarge,
            color = secondaryLyricColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class CoverLyricsPreview(
    val currentLine: String,
    val nextLine: String
)

private fun resolveCoverLyricsPreview(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    fallback: String
): CoverLyricsPreview {
    if (lyrics.isEmpty()) {
        val line = fallback.ifBlank { "暂无歌词" }
        return CoverLyricsPreview(
            currentLine = line,
            nextLine = "轻触上方可切换歌词页"
        )
    }

    val safeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val currentLine = lyrics[safeIndex].text.ifBlank { fallback.ifBlank { "暂无歌词" } }
    val nextLine = lyrics.getOrNull(safeIndex + 1)?.text?.ifBlank { "" }.orEmpty()

    return CoverLyricsPreview(
        currentLine = currentLine,
        nextLine = if (nextLine.isBlank()) " " else nextLine
    )
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
            color = Color(0xFF6A727A),
            textAlign = TextAlign.Center
        )
    }
}
