package com.music.myapplication.domain.model

data class SearchSuggestion(
    val text: String,
    val type: SuggestionType = SuggestionType.KEYWORD
)

enum class SuggestionType {
    KEYWORD,
    SONG,
    ARTIST,
    ALBUM
}
