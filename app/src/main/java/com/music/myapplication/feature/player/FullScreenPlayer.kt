package com.music.myapplication.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.ui.theme.playerGradientBackground
import com.music.myapplication.ui.theme.rememberDominantColorState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue

@Composable
fun FullScreenPlayer(
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

    val isDark = isSystemInDarkTheme()
    val dominantColorState = rememberDominantColorState(coverUrl = track.coverUrl)
    val animatedDominant by animateColorAsState(
        targetValue = dominantColorState.dominantColor,
        animationSpec = tween(800),
        label = "dominant"
    )
    val baseColor = if (isDark) Color.Black else Color(0xFF1A1A2E)
    val tintedBase = lerp(baseColor, animatedDominant, 0.05f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .playerGradientBackground(dominantColor = animatedDominant, baseColor = tintedBase)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            tintedBase.copy(alpha = 0.4f),
                            tintedBase.copy(alpha = 0.75f),
                            tintedBase.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RotatingCover(
                coverUrl = track.coverUrl,
                isPlaying = staticState.isPlaying,
                glowColor = animatedDominant,
                modifier = Modifier.size(280.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            PlayerControlsSection(
                staticState = staticState,
                progress = progress,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleMode = onToggleMode,
                onToggleFavorite = onToggleFavorite,
                onQualityChange = onQualityChange,
                accentColor = animatedDominant,
                useLightContent = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
