package com.music.myapplication.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.music.myapplication.domain.model.LyricLine

@Composable
internal fun LyricsPanelContent(
    lyricsState: LyricsUiState,
    currentIndex: Int,
    onLyricLongPress: (LyricLine) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = lyricsPanelStatusText(lyricsState)

    if (statusText != null) {
        StatusHint(
            text = statusText,
            modifier = modifier
        )
    } else {
        LyricsView(
            lyrics = lyricsState.lyrics,
            currentIndex = currentIndex,
            onLineLongPress = onLyricLongPress,
            modifier = modifier
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
            text = normalizeLyricsStatusHint(text),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
