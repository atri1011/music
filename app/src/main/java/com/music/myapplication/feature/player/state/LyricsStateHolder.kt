package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.feature.player.LyricsParser
import com.music.myapplication.feature.player.LyricsPanelMode
import com.music.myapplication.feature.player.LyricsUiState
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
import javax.inject.Inject

class LyricsStateHolder @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository
) {
    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    fun bind(scope: CoroutineScope) {
        scope.launch {
            stateStore.state
                .map { it.currentTrack }
                .distinctUntilChangedBy { it?.songKey() }
                .collectLatest { track ->
                    val preservedMode = _uiState.value.viewMode
                    if (track == null) {
                        _uiState.value = LyricsUiState(viewMode = preservedMode)
                        return@collectLatest
                    }
                    loadLyrics(track, preservedMode)
                }
        }
    }

    fun setLyricsPanelMode(mode: LyricsPanelMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun showLyricsPanel() = setLyricsPanelMode(LyricsPanelMode.COVER)

    private suspend fun loadLyrics(track: Track, viewMode: LyricsPanelMode) {
        val songKey = track.songKey()
        _uiState.value = LyricsUiState(songKey = songKey, isLoading = true, viewMode = viewMode)

        val cachedLyrics = localRepo.getCachedLyrics(track.platform.id, track.id)
        if (stateStore.state.value.currentTrack?.songKey() != songKey) return
        if (!cachedLyrics.isNullOrBlank()) {
            val cachedTranslation = localRepo.getCachedTranslation(track.platform.id, track.id)
            if (stateStore.state.value.currentTrack?.songKey() != songKey) return
            _uiState.value = LyricsUiState(
                songKey = songKey,
                lyrics = LyricsParser.parseMerged(cachedLyrics, cachedTranslation),
                viewMode = viewMode
            )
            return
        }

        when (val result = onlineRepo.getLyrics(track.platform, track.id)) {
            is Result.Success -> {
                val data = result.data
                if (data.lyric.isNotBlank()) {
                    localRepo.cacheLyrics(track.platform.id, track.id, data.lyric)
                    if (!data.translation.isNullOrBlank()) {
                        localRepo.cacheTranslation(track.platform.id, track.id, data.translation)
                    }
                }
                if (stateStore.state.value.currentTrack?.songKey() != songKey) return
                _uiState.value = LyricsUiState(
                    songKey = songKey,
                    lyrics = LyricsParser.parseMerged(data.lyric, data.translation),
                    viewMode = viewMode
                )
            }
            is Result.Error -> {
                if (stateStore.state.value.currentTrack?.songKey() != songKey) return
                _uiState.value = LyricsUiState(
                    songKey = songKey,
                    errorMessage = result.error.message.ifBlank { "歌词加载失败" },
                    viewMode = viewMode
                )
            }
            Result.Loading -> Unit
        }
    }
}
