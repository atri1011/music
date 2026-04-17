package com.music.myapplication.feature.player

internal fun lyricsPanelStatusText(lyricsState: LyricsUiState): String? = when {
    lyricsState.isLoading && lyricsState.lyrics.isEmpty() -> "歌词加载中..."
    !lyricsState.errorMessage.isNullOrBlank() && lyricsState.lyrics.isEmpty() -> lyricsState.errorMessage
    else -> null
}

internal fun normalizeLyricsStatusHint(text: String?): String =
    text.orEmpty().ifBlank { "暂无歌词" }