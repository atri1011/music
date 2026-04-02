package com.music.myapplication.data.repository

import com.music.myapplication.domain.model.AudioSource

data class PlaybackSourceResolution(
    val playableUrl: String,
    val requestedSource: AudioSource,
    val resolvedSource: AudioSource,
    val didFallback: Boolean,
    val fallbackReason: String? = null
)
