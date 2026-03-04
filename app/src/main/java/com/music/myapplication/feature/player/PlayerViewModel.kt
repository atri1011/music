package com.music.myapplication.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.session.MediaControllerConnector
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MiniPlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val quality: String = "128k"
)

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

    val miniPlayerState: StateFlow<MiniPlayerUiState> = playbackState
        .map { state ->
            MiniPlayerUiState(
                currentTrack = state.currentTrack,
                isPlaying = state.isPlaying,
                quality = state.quality
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MiniPlayerUiState())

    private val _lyricsUiState = MutableStateFlow(LyricsUiState())
    val lyricsUiState: StateFlow<LyricsUiState> = _lyricsUiState.asStateFlow()
    @Volatile
    private var currentQuality: String = "128k"

    init {
        connector.connect()
        viewModelScope.launch {
            preferences.playbackMode.collect { mode ->
                modeManager.setMode(mode)
            }
        }
        viewModelScope.launch {
            preferences.quality.collect { quality ->
                currentQuality = quality
                stateStore.updateQuality(quality)
            }
        }
        observeCurrentTrackLyrics()
    }

    fun playTrack(track: Track, queue: List<Track>, index: Int) {
        viewModelScope.launch {
            val playable = resolveTrackForPlayback(track, currentQuality) ?: return@launch
            connector.playTrack(playable, queue, index)
            localRepo.recordRecentPlay(playable)
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) connector.pause() else connector.play()
    }

    fun seekTo(positionMs: Long) = connector.seekTo(positionMs)

    fun skipNext() {
        viewModelScope.launch {
            val next = modeManager.getNextTrack() ?: return@launch
            val playable = resolveTrackForPlayback(next, currentQuality) ?: return@launch
            connector.skipToNext(playable)
            localRepo.recordRecentPlay(playable)
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            val prev = modeManager.getPreviousTrack() ?: return@launch
            val playable = resolveTrackForPlayback(prev, currentQuality) ?: return@launch
            connector.skipToPrevious(playable)
            localRepo.recordRecentPlay(playable)
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
            stateStore.updateTrack(track.copy(isFavorite = !track.isFavorite))
        }
    }

    fun setQuality(quality: String) {
        viewModelScope.launch {
            preferences.setQuality(quality)
            stateStore.updateQuality(quality)
        }
    }

    fun setLyricsPanelMode(mode: LyricsPanelMode) {
        _lyricsUiState.update { it.copy(viewMode = mode) }
    }

    fun showLyricsPanel() = setLyricsPanelMode(LyricsPanelMode.LYRICS)

    private fun observeCurrentTrackLyrics() {
        viewModelScope.launch {
            stateStore.state
                .map { it.currentTrack }
                .distinctUntilChangedBy { track -> track?.lyricsSongKey() }
                .collectLatest { track ->
                    val preservedMode = _lyricsUiState.value.viewMode
                    if (track == null) {
                        _lyricsUiState.value = LyricsUiState(viewMode = preservedMode)
                        return@collectLatest
                    }
                    loadLyrics(track = track, viewMode = preservedMode)
                }
        }
    }

    private suspend fun loadLyrics(track: Track, viewMode: LyricsPanelMode) {
        val songKey = track.lyricsSongKey()
        _lyricsUiState.value = LyricsUiState(
            songKey = songKey,
            isLoading = true,
            viewMode = viewMode
        )

        val cachedLyrics = localRepo.getCachedLyrics(track.platform.id, track.id)
        if (!cachedLyrics.isNullOrBlank()) {
            _lyricsUiState.value = LyricsUiState(
                songKey = songKey,
                lyrics = LyricsParser.parse(cachedLyrics),
                viewMode = viewMode
            )
            return
        }

        when (val result = onlineRepo.getLyrics(track.platform, track.id)) {
            is Result.Success -> {
                if (result.data.isNotBlank()) {
                    localRepo.cacheLyrics(track.platform.id, track.id, result.data)
                }
                _lyricsUiState.value = LyricsUiState(
                    songKey = songKey,
                    lyrics = LyricsParser.parse(result.data),
                    viewMode = viewMode
                )
            }

            is Result.Error -> {
                _lyricsUiState.value = LyricsUiState(
                    songKey = songKey,
                    errorMessage = result.error.message.ifBlank { "歌词加载失败" },
                    viewMode = viewMode
                )
            }

            Result.Loading -> Unit
        }
    }

    private suspend fun resolveTrackForPlayback(track: Track, quality: String): Track? {
        when (val result = onlineRepo.resolvePlayableUrl(track.platform, track.id, quality)) {
            is Result.Success -> return track.copy(playableUrl = result.data, quality = quality)
            is Result.Error -> {
                // QQ 榜单偶发数字 id 解析失败，兜底用搜索结果中的 mid 重试一次。
                if (track.platform != Platform.QQ || !track.id.isDigitsOnly()) return null
            }
            Result.Loading -> return null
        }

        val candidate = findQqMidCandidate(track) ?: return null
        val retry = onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, quality)
        if (retry !is Result.Success) return null

        return track.copy(
            id = candidate.id,
            title = if (track.title.isBlank()) candidate.title else track.title,
            artist = if (track.artist.isBlank()) candidate.artist else track.artist,
            album = if (track.album.isBlank()) candidate.album else track.album,
            coverUrl = if (track.coverUrl.isBlank()) candidate.coverUrl else track.coverUrl,
            durationMs = if (track.durationMs <= 0L) candidate.durationMs else track.durationMs,
            playableUrl = retry.data,
            quality = quality
        )
    }

    private suspend fun findQqMidCandidate(track: Track): Track? {
        val keyword = listOf(track.title, track.artist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val searchResult = onlineRepo.search(
            platform = Platform.QQ,
            keyword = keyword,
            page = 1,
            pageSize = 20
        )
        val candidates = (searchResult as? Result.Success)?.data.orEmpty()
            .filter { it.id.isNotBlank() && !it.id.isDigitsOnly() }
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            candidate.title.equals(track.title, ignoreCase = true) &&
                candidate.artist.isLikelySameArtist(track.artist)
        } ?: candidates.firstOrNull { candidate ->
            candidate.title.equals(track.title, ignoreCase = true)
        } ?: candidates.firstOrNull()
    }

    override fun onCleared() {
        connector.disconnect()
        super.onCleared()
    }
}

private fun Track.lyricsSongKey(): String = "${platform.id}:$id"
private fun String.isDigitsOnly(): Boolean = isNotBlank() && all { it.isDigit() }
private fun String.isLikelySameArtist(other: String): Boolean {
    if (isBlank() || other.isBlank()) return true
    val left = lowercase().replace(" ", "")
    val right = other.lowercase().replace(" ", "")
    if (left.contains(right) || right.contains(left)) return true
    val leftTokens = left.split(Regex("[,，/&、]")).map { it.trim() }.filter { it.isNotBlank() }
    val rightTokens = right.split(Regex("[,，/&、]")).map { it.trim() }.filter { it.isNotBlank() }
    return leftTokens.any { token -> rightTokens.any { it == token || it.contains(token) || token.contains(it) } }
}
