package com.music.myapplication.feature.player

import com.music.myapplication.domain.model.LyricLine

data class LyricsUiState(
    val songKey: String? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val viewMode: LyricsPanelMode = LyricsPanelMode.LYRICS
)

enum class LyricsPanelMode {
    LYRICS,
    COVER
}
