package com.music.myapplication.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val platform: Platform,
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val playableUrl: String = "",
    val isFavorite: Boolean = false,
    val quality: String = "128k"
)
