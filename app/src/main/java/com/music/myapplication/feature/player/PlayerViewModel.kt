package com.music.myapplication.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.TrackComment
import com.music.myapplication.domain.repository.TrackCommentSort
import com.music.myapplication.feature.player.state.CommentsStateHolder
import com.music.myapplication.feature.player.state.LyricsStateHolder
import com.music.myapplication.feature.player.state.PlaybackControlStateHolder
import com.music.myapplication.feature.player.state.SleepTimerState
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.feature.player.state.TrackInfoStateHolder
import com.music.myapplication.media.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MiniPlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val quality: String = "128k"
)

data class PlayerStaticUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val quality: String = "128k",
    val speed: Float = 1.0f
)

data class PlaybackProgressUiState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class TrackActionUiState(
    val isResolving: Boolean = false,
    val resolvingTrackKey: String? = null,
    val errorMessage: String? = null,
    val errorId: Long = 0L,
    val infoMessage: String? = null,
    val infoId: Long = 0L,
    val downloadPermissionTrack: Track? = null,
    val downloadPermissionRequestId: Long = 0L
)

data class TrackInfoUiState(
    val firstPlayDate: Long? = null,
    val totalPlayCount: Int = 0,
    val similarTracks: List<Track> = emptyList()
)

data class TrackCommentsUiState(
    val trackKey: String? = null,
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val sourcePlatform: Platform? = null,
    val totalCount: Int = 0,
    val selectedSort: TrackCommentSort = TrackCommentSort.HOT,
    val hotComments: List<TrackComment> = emptyList(),
    val latestComments: List<TrackComment> = emptyList(),
    val recommendedComments: List<TrackComment> = emptyList(),
    val errorMessage: String? = null
) {
    fun commentsOf(sort: TrackCommentSort): List<TrackComment> = when (sort) {
        TrackCommentSort.HOT -> hotComments
        TrackCommentSort.LATEST -> latestComments
        TrackCommentSort.RECOMMENDED -> recommendedComments
    }

    val visibleComments: List<TrackComment>
        get() = commentsOf(selectedSort)

    val availableSorts: List<TrackCommentSort>
        get() = TrackCommentSort.entries.filter { commentsOf(it).isNotEmpty() }
}

data class PlaylistMutationResult(
    val playlistId: String,
    val playlistName: String,
    val added: Boolean,
    val created: Boolean = false
) {
    val message: String
        get() = when {
            created && added -> "已添加到新歌单「$playlistName」"
            added -> "已添加到「$playlistName」"
            else -> "歌曲已在「$playlistName」里了"
        }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackControlStateHolder,
    private val lyrics: LyricsStateHolder,
    private val comments: CommentsStateHolder,
    private val trackInfo: TrackInfoStateHolder,
    private val sleepTimer: SleepTimerStateHolder,
    private val queueManager: QueueManager,
    private val localRepo: LocalLibraryRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> get() = playback.playbackState
    val miniPlayerState: StateFlow<MiniPlayerUiState> get() = playback.miniPlayerState
    val staticUiState: StateFlow<PlayerStaticUiState> get() = playback.staticUiState
    val progressState: StateFlow<PlaybackProgressUiState> get() = playback.progressState
    val miniProgressState: StateFlow<Float> get() = playback.miniProgressState
    val trackActionState: StateFlow<TrackActionUiState> get() = playback.trackActionState

    val lyricsUiState: StateFlow<LyricsUiState> get() = lyrics.uiState
    val commentsUiState: StateFlow<TrackCommentsUiState> get() = comments.uiState
    val trackInfoState: StateFlow<TrackInfoUiState> get() = trackInfo.uiState

    val sleepTimerState: StateFlow<SleepTimerState> get() = sleepTimer.state
    val playlists: StateFlow<List<Playlist>> = localRepo.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        playback.bind(viewModelScope)
        lyrics.bind(viewModelScope)
        comments.bind(viewModelScope)
        trackInfo.bind(viewModelScope)
        sleepTimer.bind(viewModelScope)
        sleepTimer.onTimerExpired = playback::pausePlayback
    }

    fun playTrack(track: Track, queue: List<Track>, index: Int) = playback.playTrack(track, queue, index)
    fun togglePlayPause() = playback.togglePlayPause()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun skipNext() = playback.skipNext()
    fun skipPrevious() = playback.skipPrevious()
    fun togglePlaybackMode() = playback.togglePlaybackMode()
    fun toggleFavorite() = playback.toggleFavorite()
    fun downloadTrack(track: Track) = playback.downloadTrack(track)
    fun consumeDownloadPermissionRequest() = playback.consumeDownloadPermissionRequest()
    fun onDownloadPermissionResult(track: Track, granted: Boolean) =
        playback.onDownloadPermissionResult(track, granted)
    fun setQuality(quality: String) = playback.setQuality(quality)
    fun setSpeed(speed: Float) = playback.setSpeed(speed)

    fun setLyricsPanelMode(mode: LyricsPanelMode) = lyrics.setLyricsPanelMode(mode)
    fun showLyricsPanel() = lyrics.showLyricsPanel()
    fun clearTrackActionError() = playback.clearTrackActionError()
    fun clearTrackActionInfo() = playback.clearTrackActionInfo()

    fun showComments() = comments.showComments()
    fun hideComments() = comments.hideComments()
    fun retryLoadComments() = comments.retryLoadComments()
    fun selectCommentSort(sort: TrackCommentSort) = comments.selectCommentSort(sort)

    suspend fun addTrackToPlaylist(playlist: Playlist, track: Track): PlaylistMutationResult {
        val alreadyExists = localRepo.getPlaylistSongs(playlist.id)
            .first()
            .any { candidate ->
                candidate.id == track.id && candidate.platform == track.platform
            }
        if (alreadyExists) {
            return PlaylistMutationResult(
                playlistId = playlist.id,
                playlistName = playlist.name,
                added = false
            )
        }

        localRepo.addToPlaylist(playlist.id, track)
        return PlaylistMutationResult(
            playlistId = playlist.id,
            playlistName = playlist.name,
            added = true
        )
    }

    suspend fun createPlaylistAndAddTrack(name: String, track: Track): PlaylistMutationResult {
        val playlistName = name.trim()
        require(playlistName.isNotEmpty()) { "歌单名称不能为空" }

        val playlist = localRepo.createPlaylist(playlistName)
        localRepo.addToPlaylist(playlist.id, track)
        return PlaylistMutationResult(
            playlistId = playlist.id,
            playlistName = playlist.name,
            added = true,
            created = true
        )
    }

    // Sleep timer
    fun startSleepTimer(minutes: Int) = sleepTimer.startCountdown(minutes)
    fun startSleepTimerAfterTrack() = sleepTimer.startAfterCurrentTrack()
    fun cancelSleepTimer() = sleepTimer.cancel()

    // Queue management
    fun moveQueueItem(from: Int, to: Int) {
        queueManager.moveItem(from, to)
        playback.refreshQueueState()
    }

    fun removeFromQueue(index: Int) {
        val wasCurrent = index == queueManager.currentIndex
        val shouldAutoPlay = playbackState.value.isPlaying
        queueManager.removeFromQueue(index)
        when {
            queueManager.isEmpty -> playback.stopPlayback()
            wasCurrent -> {
                val replacement = queueManager.currentTrack ?: run {
                    playback.refreshQueueState()
                    return
                }
                playback.syncQueueState(currentTrack = replacement)
                playback.loadQueueTrack(
                    track = replacement,
                    queue = queueManager.queue,
                    index = queueManager.currentIndex,
                    autoPlay = shouldAutoPlay
                )
            }
            else -> playback.refreshQueueState()
        }
    }

    fun clearQueue() {
        if (queueManager.isEmpty) return
        queueManager.clear()
        playback.stopPlayback()
    }

    fun playQueueItem(index: Int) {
        val track = queueManager.queue.getOrNull(index) ?: return
        playback.playTrack(track, queueManager.queue, index)
    }

    override fun onCleared() {
        playback.unbind()
        sleepTimer.cancel()
        super.onCleared()
    }
}
