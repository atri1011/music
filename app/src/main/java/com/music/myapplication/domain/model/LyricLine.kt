package com.music.myapplication.domain.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String = ""
)
