package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.DispatchersProvider
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.MiniPlayerUiState
import com.music.myapplication.feature.player.PlaybackProgressUiState
import com.music.myapplication.feature.player.PlayerStaticUiState
import com.music.myapplication.feature.player.TrackActionUiState
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.session.MediaControllerConnector
import com.music.myapplication.media.state.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

class PlaybackControlStateHolder @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val connector: MediaControllerConnector,
    private val modeManager: PlaybackModeManager,
    private val queueManager: QueueManager,
    private val localRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences,
    private val resolver: TrackPlaybackResolver,
    private val dispatchers: DispatchersProvider
) {
    private lateinit var scope: CoroutineScope

    private val _trackActionState = MutableStateFlow(TrackActionUiState())
    val trackActionState: StateFlow<TrackActionUiState> = _trackActionState.asStateFlow()
    private var nextTrackActionErrorId = 0L

    @Volatile
    private var currentQuality: String = "128k"

    lateinit var playbackState: StateFlow<PlaybackState>
        private set
    lateinit var miniPlayerState: StateFlow<MiniPlayerUiState>
        private set
    lateinit var staticUiState: StateFlow<PlayerStaticUiState>
        private set
    lateinit var progressState: StateFlow<PlaybackProgressUiState>
        private set
    lateinit var miniProgressState: StateFlow<Float>
        private set

    fun bind(scope: CoroutineScope) {
        this.scope = scope
        val initial = stateStore.state.value

        playbackState = stateStore.state
            .stateIn(scope, SharingStarted.Eagerly, initial)

        miniPlayerState = playbackState
            .map { s -> MiniPlayerUiState(s.currentTrack, s.isPlaying, s.quality) }
            .distinctUntilChanged()
            .stateIn(
                scope, SharingStarted.Eagerly,
                MiniPlayerUiState(initial.currentTrack, initial.isPlaying, initial.quality)
            )

        staticUiState = stateStore.state
            .map { s ->
                PlayerStaticUiState(
                    s.currentTrack, s.isPlaying, s.playbackMode,
                    s.queue, s.currentIndex, s.quality
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope, SharingStarted.Eagerly,
                PlayerStaticUiState(
                    initial.currentTrack, initial.isPlaying, initial.playbackMode,
                    initial.queue, initial.currentIndex, initial.quality
                )
            )

        progressState = stateStore.state
            .map { s -> PlaybackProgressUiState(s.positionMs, s.durationMs) }
            .distinctUntilChanged()
            .stateIn(
                scope, SharingStarted.WhileSubscribed(5000),
                PlaybackProgressUiState(initial.positionMs, initial.durationMs)
            )

        val initialProgress = if (initial.durationMs > 0L)
            ((initial.positionMs.toFloat() / initial.durationMs).coerceIn(0f, 1f) * 100f).roundToInt() / 100f
        else 0f
        miniProgressState = progressState
            .map { p ->
                if (p.durationMs > 0L) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
                else 0f
            }
            .map { (it * 100f).roundToInt() / 100f }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), initialProgress)

        connector.connect()

        scope.launch {
            preferences.playbackMode.collect { mode -> modeManager.setMode(mode) }
        }
        scope.launch {
            preferences.quality.collect { quality ->
                currentQuality = quality
                stateStore.updateQuality(quality)
            }
        }
    }

    fun unbind() {
        connector.disconnect()
    }

    fun playTrack(track: Track, queue: List<Track>, index: Int) {
        resolveTrackAction(track) { playable ->
            connector.playTrack(playable, queue, index)
            withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
        }
    }

    fun loadQueueTrack(track: Track, queue: List<Track>, index: Int, autoPlay: Boolean) {
        resolveTrackAction(
            track = track,
            onFailure = {
                syncQueueState(currentTrack = track, clearPreparedTrack = true)
            }
        ) { playable ->
            connector.loadTrack(playable, queue, index, autoPlay)
            if (autoPlay) {
                withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
            }
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) {
            connector.pause()
            return
        }

        val currentTrack = playbackState.value.currentTrack ?: run {
            connector.play()
            return
        }
        val queue = queueManager.queue.ifEmpty { playbackState.value.queue }
        val targetQueue = if (queue.isEmpty()) listOf(currentTrack) else queue
        val targetIndex = when {
            queueManager.currentIndex >= 0 -> queueManager.currentIndex
            playbackState.value.currentIndex >= 0 -> playbackState.value.currentIndex
            else -> 0
        }.coerceIn(0, targetQueue.lastIndex)

        if (!connector.hasMediaItem()) {
            playTrack(currentTrack, targetQueue, targetIndex)
            return
        }

        connector.play()
    }

    fun pausePlayback() {
        if (playbackState.value.isPlaying) {
            connector.pause()
        }
    }

    fun stopPlayback() = connector.stop()

    fun seekTo(positionMs: Long) = connector.seekTo(positionMs)

    fun skipNext() {
        val previousIndex = queueManager.currentIndex
        val nextTrack = modeManager.getNextTrack() ?: return
        resolveTrackAction(
            track = nextTrack,
            onFailure = {
                if (previousIndex >= 0) {
                    queueManager.moveToIndex(previousIndex)
                }
                refreshQueueState()
            }
        ) { playable ->
            connector.skipToNext(playable)
            withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
        }
    }

    fun skipPrevious() {
        val previousIndex = queueManager.currentIndex
        val previousTrack = modeManager.getPreviousTrack() ?: return
        resolveTrackAction(
            track = previousTrack,
            onFailure = {
                if (previousIndex >= 0) {
                    queueManager.moveToIndex(previousIndex)
                }
                refreshQueueState()
            }
        ) { playable ->
            connector.skipToPrevious(playable)
            withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
        }
    }

    fun togglePlaybackMode() {
        modeManager.toggleMode()
        scope.launch { preferences.setPlaybackMode(modeManager.currentMode()) }
    }

    fun toggleFavorite() {
        val track = playbackState.value.currentTrack ?: return
        scope.launch {
            localRepo.toggleFavorite(track)
            stateStore.updateTrack(track.copy(isFavorite = !track.isFavorite))
        }
    }

    fun setQuality(quality: String) {
        scope.launch {
            preferences.setQuality(quality)
            stateStore.updateQuality(quality)
        }
    }

    fun clearTrackActionError() {
        _trackActionState.update { current ->
            if (current.errorMessage.isNullOrBlank()) current else current.copy(errorMessage = null)
        }
    }

    fun refreshQueueState() {
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
    }

    fun syncQueueState(currentTrack: Track? = playbackState.value.currentTrack, clearPreparedTrack: Boolean = false) {
        if (clearPreparedTrack) {
            connector.clearPlayback()
        }
        stateStore.updateTrack(currentTrack)
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
    }

    private fun resolveTrackAction(
        track: Track,
        onFailure: () -> Unit = {},
        onResolved: suspend (Track) -> Unit
    ) {
        scope.launch {
            if (_trackActionState.value.isResolving) return@launch
            _trackActionState.update {
                it.copy(
                    isResolving = true,
                    resolvingTrackKey = track.songKey()
                )
            }
            try {
                when (val result = withContext(dispatchers.io) {
                    resolver.resolve(track, currentQuality)
                }) {
                    is Result.Success -> {
                        clearTrackActionError()
                        onResolved(result.data)
                    }
                    is Result.Error -> {
                        onFailure()
                        publishTrackActionError(
                            result.error.message.ifBlank { "解析播放地址失败，请稍后重试" }
                        )
                    }
                    Result.Loading -> Unit
                }
            } finally {
                _trackActionState.update {
                    it.copy(
                        isResolving = false,
                        resolvingTrackKey = null
                    )
                }
            }
        }
    }

    private fun publishTrackActionError(message: String) {
        nextTrackActionErrorId += 1L
        _trackActionState.update {
            it.copy(
                errorMessage = message,
                errorId = nextTrackActionErrorId
            )
        }
    }
}

internal fun Track.songKey(): String = "${platform.id}:$id"
