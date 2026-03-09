package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.DispatchersProvider
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.feature.player.TrackInfoUiState
import com.music.myapplication.media.state.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrackInfoStateHolder @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val localRepo: LocalLibraryRepository,
    private val recommendationRepo: RecommendationRepository,
    private val dispatchers: DispatchersProvider
) {
    private val _uiState = MutableStateFlow(TrackInfoUiState())
    val uiState: StateFlow<TrackInfoUiState> = _uiState.asStateFlow()

    fun bind(scope: CoroutineScope) {
        scope.launch {
            stateStore.state
                .map { it.currentTrack }
                .distinctUntilChangedBy { it?.songKey() }
                .collectLatest { track ->
                    if (track == null) {
                        _uiState.value = TrackInfoUiState()
                        return@collectLatest
                    }
                    loadTrackInfo(track)
                }
        }
    }

    private suspend fun loadTrackInfo(track: Track) {
        val trackKey = track.songKey()
        val playCount = withContext(dispatchers.io) {
            localRepo.getTrackPlayCount(track.id, track.platform.id)
        }
        if (stateStore.state.value.currentTrack?.songKey() != trackKey) return
        val firstPlayDate = withContext(dispatchers.io) {
            localRepo.getFirstPlayDate(track.id, track.platform.id)
        }
        if (stateStore.state.value.currentTrack?.songKey() != trackKey) return
        _uiState.value = TrackInfoUiState(
            firstPlayDate = firstPlayDate,
            totalPlayCount = playCount
        )
        val similar = withContext(dispatchers.io) {
            recommendationRepo.getSimilarTracks(track)
        }
        if (stateStore.state.value.currentTrack?.songKey() != trackKey) return
        _uiState.update { it.copy(similarTracks = similar) }
    }
}
