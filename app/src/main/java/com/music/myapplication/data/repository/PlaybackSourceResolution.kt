package com.music.myapplication.data.repository

import com.music.myapplication.domain.model.AudioSourceDescriptor

data class PlaybackSourceResolution(
    val playableUrl: String,
    val requestedSource: AudioSourceDescriptor,
    val resolvedSource: AudioSourceDescriptor,
    val didFallback: Boolean,
    val fallbackReason: String? = null
)
