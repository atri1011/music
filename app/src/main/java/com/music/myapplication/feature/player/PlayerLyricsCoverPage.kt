package com.music.myapplication.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import kotlin.math.abs

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun CoverPage(
    track: Track,
    isPlaying: Boolean,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onCoverLongPress: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    sharedArtworkVisible: Boolean = true,
    modifier: Modifier = Modifier,
    artworkSize: Dp = 252.dp,
    showLyricsPreview: Boolean = true,
    compactLayout: Boolean = false
) {
    val previewLines = remember(lyrics, currentIndex) {
        resolveCoverLyricsPreview(lyrics = lyrics, currentIndex = currentIndex, fallback = "暂无歌词")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PlayerArtwork(
            track = track,
            isPlaying = isPlaying,
            onPreviousTrack = onPreviousTrack,
            onNextTrack = onNextTrack,
            onLongPress = onCoverLongPress,
            sharedTransitionScope = sharedTransitionScope,
            sharedArtworkVisible = sharedArtworkVisible,
            modifier = Modifier.size(artworkSize)
        )
        Spacer(modifier = Modifier.height(if (compactLayout) 12.dp else 22.dp))
        Text(
            text = track.title,
            style = (if (compactLayout) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                .copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.artist,
            style = if (compactLayout) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showLyricsPreview) {
            Spacer(modifier = Modifier.height(24.dp))
            CoverLyricsPreviewBlock(
                previewLines = previewLines,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlayerArtwork(
    track: Track,
    isPlaying: Boolean,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onLongPress: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    sharedArtworkVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var transitionDirection by remember { mutableStateOf(0) }

    AnimatedContent(
        targetState = track,
        transitionSpec = { artworkTrackChangeTransform(transitionDirection) },
        label = "coverArtworkTrackChange",
        modifier = modifier.trackSwipeGesture(
            onSwipePrevious = {
                transitionDirection = -1
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPreviousTrack()
            },
            onSwipeNext = {
                transitionDirection = 1
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNextTrack()
            }
        )
    ) { animatedTrack ->
        RotatingCover(
            coverUrl = animatedTrack.coverUrl,
            isPlaying = isPlaying,
            glowColor = Color.White.copy(alpha = 0.15f),
            onLongPress = onLongPress,
            modifier = Modifier
                .sharedTrackArtwork(
                    sharedTransitionScope = sharedTransitionScope,
                    track = animatedTrack,
                    visible = sharedArtworkVisible
                )
                .fillMaxSize()
        )
    }
}

@Composable
private fun Modifier.artworkLongPress(onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(onLongPress) {
        detectTapGestures(
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            }
        )
    }
}

private fun Modifier.trackSwipeGesture(
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
): Modifier = pointerInput(onSwipePrevious, onSwipeNext) {
    var totalDragX = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDragX = 0f },
        onHorizontalDrag = { change, dragAmount ->
            totalDragX += dragAmount
            if (abs(totalDragX) > 12f) {
                change.consume()
            }
        },
        onDragEnd = {
            val threshold = size.width * 0.22f
            when {
                totalDragX > threshold -> onSwipePrevious()
                totalDragX < -threshold -> onSwipeNext()
            }
            totalDragX = 0f
        },
        onDragCancel = { totalDragX = 0f }
    )
}

private fun artworkTrackChangeTransform(direction: Int): ContentTransform {
    val sign = when {
        direction < 0 -> -1
        direction > 0 -> 1
        else -> 1
    }
    return (slideInHorizontally(
        animationSpec = tween(durationMillis = 220),
        initialOffsetX = { width -> sign * width / 4 }
    ) + fadeIn(animationSpec = spring())) togetherWith
        (slideOutHorizontally(
            animationSpec = tween(durationMillis = 180),
            targetOffsetX = { width -> -sign * width / 5 }
        ) + fadeOut(animationSpec = tween(durationMillis = 160)))
}

@Composable
private fun CoverLyricsPreviewBlock(
    previewLines: CoverLyricsPreview,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = previewLines.currentLine,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = previewLines.nextLine,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
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
        nextLine = nextLine
    )
}
