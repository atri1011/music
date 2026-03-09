package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.DispatchersProvider
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

class PlaybackControlStateHolder @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val connector: MediaControllerConnector,
    private val modeManager: PlaybackModeManager,
    private val localRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences,
    private val resolver: TrackPlaybackResolver,
    private val dispatchers: DispatchersProvider
) {
    private lateinit var scope: CoroutineScope

    private val _trackActionState = MutableStateFlow(TrackActionUiState())
    val trackActionState: StateFlow<TrackActionUiState> = _trackActionState.asStateFlow()

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
        scope.launch {
            if (_trackActionState.value.isResolving) return@launch
            _trackActionState.value = TrackActionUiState(
                isResolving = true,
                resolvingTrackKey = track.songKey()
            )
            try {
                val playable = withContext(dispatchers.io) {
                    resolver.resolve(track, currentQuality)
                } ?: return@launch
                connector.playTrack(playable, queue, index)
                withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
            } finally {
                _trackActionState.value = TrackActionUiState()
            }
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) connector.pause() else connector.play()
    }

    fun seekTo(positionMs: Long) = connector.seekTo(positionMs)

    fun skipNext() {
        scope.launch {
            if (_trackActionState.value.isResolving) return@launch
            val next = modeManager.getNextTrack() ?: return@launch
            _trackActionState.value = TrackActionUiState(
                isResolving = true,
                resolvingTrackKey = next.songKey()
            )
            try {
                val playable = withContext(dispatchers.io) {
                    resolver.resolve(next, currentQuality)
                } ?: return@launch
                connector.skipToNext(playable)
                withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
            } finally {
                _trackActionState.value = TrackActionUiState()
            }
        }
    }

    fun skipPrevious() {
        scope.launch {
            if (_trackActionState.value.isResolving) return@launch
            val prev = modeManager.getPreviousTrack() ?: return@launch
            _trackActionState.value = TrackActionUiState(
                isResolving = true,
                resolvingTrackKey = prev.songKey()
            )
            try {
                val playable = withContext(dispatchers.io) {
                    resolver.resolve(prev, currentQuality)
                } ?: return@launch
                connector.skipToPrevious(playable)
                withContext(dispatchers.io) { localRepo.recordRecentPlay(playable) }
            } finally {
                _trackActionState.value = TrackActionUiState()
            }
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
}

internal fun Track.songKey(): String = "${platform.id}:$id"
