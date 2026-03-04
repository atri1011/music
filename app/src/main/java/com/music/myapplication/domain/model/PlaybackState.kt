package com.music.myapplication.domain.model

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val quality: String = "128k"
)
