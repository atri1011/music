package com.music.myapplication.media.player

import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.state.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackModeManager @Inject constructor(
    private val queueManager: QueueManager,
    private val stateStore: PlaybackStateStore,
    private val preferences: PlayerPreferences
) {

    private var mode: PlaybackMode = PlaybackMode.SEQUENTIAL
    private var shuffleIndices: List<Int> = emptyList()
    private var shufflePosition: Int = -1

    fun setMode(newMode: PlaybackMode) {
        mode = newMode
        stateStore.updatePlaybackMode(mode)
        if (mode == PlaybackMode.SHUFFLE) {
            generateShuffleOrder()
        }
    }

    fun toggleMode() {
        setMode(mode.next())
    }

    fun getNextTrack(): Track? = when (mode) {
        PlaybackMode.SEQUENTIAL -> queueManager.moveToNext()
        PlaybackMode.REPEAT_ONE -> queueManager.currentTrack
        PlaybackMode.SHUFFLE -> {
            if (shuffleIndices.isEmpty()) generateShuffleOrder()
            shufflePosition++
            if (shufflePosition >= shuffleIndices.size) {
                generateShuffleOrder()
                shufflePosition = 0
            }
            queueManager.moveToIndex(shuffleIndices[shufflePosition])
        }
    }

    fun getPreviousTrack(): Track? = when (mode) {
        PlaybackMode.SEQUENTIAL -> queueManager.moveToPrevious()
        PlaybackMode.REPEAT_ONE -> queueManager.currentTrack
        PlaybackMode.SHUFFLE -> {
            if (shufflePosition > 0) {
                shufflePosition--
                queueManager.moveToIndex(shuffleIndices[shufflePosition])
            } else {
                queueManager.currentTrack
            }
        }
    }

    fun currentMode(): PlaybackMode = mode

    private fun generateShuffleOrder() {
        val size = queueManager.size
        if (size == 0) return
        shuffleIndices = (0 until size).shuffled()
        shufflePosition = -1
    }
}
