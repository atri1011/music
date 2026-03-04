package com.music.myapplication.media.state

import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateStore @Inject constructor() {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun updateTrack(track: Track?) = _state.update { current ->
        if (current.currentTrack == track) current else current.copy(currentTrack = track)
    }

    fun updatePlaying(playing: Boolean) = _state.update { current ->
        if (current.isPlaying == playing) current else current.copy(isPlaying = playing)
    }

    fun updatePosition(positionMs: Long) = _state.update { current ->
        if (current.positionMs == positionMs) current else current.copy(positionMs = positionMs)
    }

    fun updateDuration(durationMs: Long) = _state.update { current ->
        if (current.durationMs == durationMs) current else current.copy(durationMs = durationMs)
    }

    fun updatePlaybackMode(mode: PlaybackMode) = _state.update { current ->
        if (current.playbackMode == mode) current else current.copy(playbackMode = mode)
    }

    fun updateQueue(queue: List<Track>, index: Int) = _state.update {
        if (it.queue == queue && it.currentIndex == index) it
        else it.copy(queue = queue, currentIndex = index)
    }

    fun updateQuality(quality: String) = _state.update { current ->
        if (current.quality == quality) current else current.copy(quality = quality)
    }

    fun reset() = _state.update { PlaybackState() }
}
