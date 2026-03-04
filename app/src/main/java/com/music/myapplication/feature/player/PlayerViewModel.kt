package com.music.myapplication.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.session.MediaControllerConnector
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val connector: MediaControllerConnector,
    private val queueManager: QueueManager,
    private val modeManager: PlaybackModeManager,
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = stateStore.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

    init {
        connector.connect()
        viewModelScope.launch {
            preferences.playbackMode.collect { mode ->
                modeManager.setMode(mode)
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track>, index: Int) {
        viewModelScope.launch {
            val quality = preferences.quality.first()
            val result = onlineRepo.resolvePlayableUrl(track.platform, track.id, quality)
            if (result is Result.Success) {
                val playable = track.copy(playableUrl = result.data, quality = quality)
                val playableQueue = queue.mapIndexed { i, t ->
                    if (i == index) playable else t
                }
                connector.playTrack(playable, playableQueue, index)
                localRepo.recordRecentPlay(playable)
            }
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) connector.pause() else connector.play()
    }

    fun seekTo(positionMs: Long) = connector.seekTo(positionMs)

    fun skipNext() {
        viewModelScope.launch {
            val next = modeManager.getNextTrack() ?: return@launch
            val quality = preferences.quality.first()
            val result = onlineRepo.resolvePlayableUrl(next.platform, next.id, quality)
            if (result is Result.Success) {
                val playable = next.copy(playableUrl = result.data, quality = quality)
                connector.skipToNext(playable)
                localRepo.recordRecentPlay(playable)
            }
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            val prev = modeManager.getPreviousTrack() ?: return@launch
            val quality = preferences.quality.first()
            val result = onlineRepo.resolvePlayableUrl(prev.platform, prev.id, quality)
            if (result is Result.Success) {
                val playable = prev.copy(playableUrl = result.data, quality = quality)
                connector.skipToPrevious(playable)
                localRepo.recordRecentPlay(playable)
            }
        }
    }

    fun togglePlaybackMode() {
        modeManager.toggleMode()
        viewModelScope.launch {
            preferences.setPlaybackMode(modeManager.currentMode())
        }
    }

    fun toggleFavorite() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            localRepo.toggleFavorite(track)
            val isFav = localRepo.isFavorite(track.id, track.platform.id)
            stateStore.updateTrack(track.copy(isFavorite = isFav))
        }
    }

    fun setQuality(quality: String) {
        viewModelScope.launch {
            preferences.setQuality(quality)
            stateStore.updateQuality(quality)
        }
    }

    override fun onCleared() {
        connector.disconnect()
        super.onCleared()
    }
}
