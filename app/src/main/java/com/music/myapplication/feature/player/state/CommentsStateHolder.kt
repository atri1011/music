package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.TrackCommentSort
import com.music.myapplication.domain.repository.TrackCommentsResult
import com.music.myapplication.feature.player.TrackCommentsUiState
import com.music.myapplication.media.state.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class CommentsStateHolder @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val onlineRepo: OnlineMusicRepository
) {
    private lateinit var scope: CoroutineScope
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    private val _uiState = MutableStateFlow(TrackCommentsUiState())
    val uiState: StateFlow<TrackCommentsUiState> = _uiState.asStateFlow()

    fun bind(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            stateStore.state
                .map { it.currentTrack }
                .distinctUntilChangedBy { it?.songKey() }
                .collectLatest {
                    loadJob?.cancel()
                    _uiState.value = TrackCommentsUiState()
                }
        }
    }

    fun showComments() {
        val track = currentTrack() ?: return
        val trackKey = track.songKey()
        val current = _uiState.value
        if (current.isLoading && current.trackKey == trackKey) return
        if (current.trackKey == trackKey && current.hasLoaded && current.errorMessage.isNullOrBlank()) {
            _uiState.update { it.copy(isVisible = true) }
            return
        }
        loadComments(track, showSheet = true)
    }

    fun hideComments() {
        _uiState.update { it.copy(isVisible = false) }
    }

    fun retryLoadComments() {
        val track = currentTrack() ?: return
        loadComments(track, showSheet = true)
    }

    fun selectCommentSort(sort: TrackCommentSort) {
        _uiState.update { state ->
            if (state.commentsOf(sort).isEmpty()) state else state.copy(selectedSort = sort)
        }
    }

    private fun loadComments(track: Track, showSheet: Boolean) {
        val trackKey = track.songKey()
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = scope.launch {
            _uiState.value = TrackCommentsUiState(
                trackKey = trackKey, isVisible = showSheet, isLoading = true
            )
            when (val result = onlineRepo.getTrackComments(track)) {
                is Result.Success -> {
                    if (loadGeneration != generation) return@launch
                    val data = result.data
                    _uiState.value = TrackCommentsUiState(
                        trackKey = trackKey,
                        isVisible = showSheet,
                        isLoading = false,
                        hasLoaded = true,
                        sourcePlatform = data.sourcePlatform,
                        totalCount = data.totalCount,
                        selectedSort = data.preferredSort(),
                        hotComments = data.hotComments,
                        latestComments = data.latestComments,
                        recommendedComments = data.recommendedComments
                    )
                }
                is Result.Error -> {
                    if (loadGeneration != generation) return@launch
                    _uiState.value = TrackCommentsUiState(
                        trackKey = trackKey,
                        isVisible = showSheet,
                        isLoading = false,
                        hasLoaded = true,
                        errorMessage = result.error.message.ifBlank { "评论加载失败" }
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun currentTrack(): Track? = stateStore.state.value.currentTrack
}

private fun TrackCommentsResult.preferredSort(): TrackCommentSort = when {
    hotComments.isNotEmpty() -> TrackCommentSort.HOT
    latestComments.isNotEmpty() -> TrackCommentSort.LATEST
    recommendedComments.isNotEmpty() -> TrackCommentSort.RECOMMENDED
    else -> TrackCommentSort.HOT
}
