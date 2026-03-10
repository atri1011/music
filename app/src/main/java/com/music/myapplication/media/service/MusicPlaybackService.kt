package com.music.myapplication.media.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.EqualizerPreferences
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.feature.player.state.TrackPlaybackResolver
import com.music.myapplication.media.equalizer.EqualizerManager
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.session.PlaybackLoadRequest
import com.music.myapplication.media.session.loadTrackSessionCommand
import com.music.myapplication.media.session.toPlaybackLoadRequestOrNull
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var stateStore: PlaybackStateStore
    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var modeManager: PlaybackModeManager
    @Inject lateinit var sleepTimer: SleepTimerStateHolder
    @Inject lateinit var equalizerManager: EqualizerManager
    @Inject lateinit var equalizerPreferences: EqualizerPreferences
    @Inject lateinit var playerPreferences: PlayerPreferences
    @Inject lateinit var trackPlaybackResolver: TrackPlaybackResolver
    @Inject lateinit var localLibraryRepository: LocalLibraryRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null
    private var equalizerSettingsJob: Job? = null
    private var playerSettingsJob: Job? = null
    private var transitionJob: Job? = null

    private var cachedEqEnabled = false
    private var cachedEqPresetIndex = 0
    private var cachedEqCustomBands: Map<Int, Int> = emptyMap()
    private var cachedQuality = "128k"
    private var cachedCrossfadeEnabled = false
    private var cachedCrossfadeDurationMs = PlayerPreferences.DEFAULT_CROSSFADE_DURATION_MS

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )

        exoPlayer.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(sessionCallback)
            .build()

        stateStore.updateSpeed(exoPlayer.playbackParameters.speed)
        bindEqualizerToAudioSession(exoPlayer.audioSessionId)
        observeEqualizerSettings()
        observePlayerSettings()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        positionUpdateJob?.cancel()
        equalizerSettingsJob?.cancel()
        playerSettingsJob?.cancel()
        transitionJob?.cancel()
        equalizerManager.release()
        setPlayerVolume(1f)
        mediaSession?.run {
            player.removeListener(playerListener)
            player.stop()
            player.clearMediaItems()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                stateStore.updatePosition(exoPlayer.currentPosition)
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun observePlayerSettings() {
        playerSettingsJob?.cancel()
        playerSettingsJob = serviceScope.launch {
            combine(
                playerPreferences.quality,
                playerPreferences.crossfadeEnabled,
                playerPreferences.crossfadeDurationMs
            ) { quality, crossfadeEnabled, crossfadeDurationMs ->
                Triple(quality, crossfadeEnabled, crossfadeDurationMs)
            }.collect { (quality, crossfadeEnabled, crossfadeDurationMs) ->
                cachedQuality = quality
                cachedCrossfadeEnabled = crossfadeEnabled
                cachedCrossfadeDurationMs = crossfadeDurationMs
                if (!crossfadeEnabled) {
                    cancelActiveTransition()
                }
            }
        }
    }

    private fun bindEqualizerToAudioSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            equalizerManager.release()
            return
        }
        equalizerManager.bindToAudioSession(audioSessionId)
        applyEqualizerSettings()
    }

    private fun observeEqualizerSettings() {
        equalizerSettingsJob?.cancel()
        equalizerSettingsJob = serviceScope.launch {
            combine(
                equalizerPreferences.enabled,
                equalizerPreferences.presetIndex,
                equalizerPreferences.customBandLevels
            ) { enabled, presetIndex, customBands ->
                Triple(enabled, presetIndex, customBands)
            }.collect { (enabled, presetIndex, customBands) ->
                cachedEqEnabled = enabled
                cachedEqPresetIndex = presetIndex
                cachedEqCustomBands = customBands
                applyEqualizerSettings()
            }
        }
    }

    private fun applyEqualizerSettings() {
        equalizerManager.setEnabled(cachedEqEnabled)
        if (!cachedEqEnabled) return
        if (cachedEqPresetIndex >= 0) {
            equalizerManager.setPreset(cachedEqPresetIndex)
        } else {
            equalizerManager.setBandLevels(cachedEqCustomBands)
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands
                .buildUpon()
                .add(loadTrackSessionCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                defaultResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                loadTrackSessionCommand.customAction -> {
                    val request = args.toPlaybackLoadRequestOrNull()
                    if (request == null) {
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    } else {
                        handleLoadTrackRequest(request)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }

    private fun handleLoadTrackRequest(request: PlaybackLoadRequest) {
        queueManager.setQueue(request.queue, request.index)
        if (queueManager.currentIndex >= 0) {
            queueManager.updateTrack(queueManager.currentIndex, request.track)
        }
        stateStore.updateTrack(request.track)
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
        stateStore.updatePosition(request.startPositionMs.coerceAtLeast(0L))
        stateStore.updateDuration(request.track.durationMs.coerceAtLeast(0L))
        if (!request.autoPlay) {
            stateStore.updatePlaying(false)
        }
        if (request.track.playableUrl.isBlank()) return

        launchTransition {
            loadTrackOnPlayer(
                track = request.track,
                autoPlay = request.autoPlay,
                startPositionMs = request.startPositionMs,
                transitionMode = if (shouldUseCrossfade(request.autoPlay)) {
                    CrossfadeTransitionMode.FADE_THROUGH
                } else {
                    CrossfadeTransitionMode.DIRECT
                }
            )
        }
    }

    private fun launchTransition(block: suspend () -> Unit) {
        cancelActiveTransition()
        val job = serviceScope.launch { block() }
        transitionJob = job
        job.invokeOnCompletion {
            if (transitionJob === job) {
                transitionJob = null
            }
        }
    }

    private fun cancelActiveTransition() {
        transitionJob?.cancel()
        transitionJob = null
        setPlayerVolume(1f)
    }

    private fun shouldUseCrossfade(autoPlay: Boolean): Boolean =
        autoPlay &&
            cachedCrossfadeEnabled &&
            exoPlayer.isPlaying &&
            exoPlayer.mediaItemCount > 0

    private suspend fun loadTrackOnPlayer(
        track: Track,
        autoPlay: Boolean,
        startPositionMs: Long,
        transitionMode: CrossfadeTransitionMode
    ) {
        val mediaItem = MediaItem.fromUri(track.playableUrl)
        try {
            when (transitionMode) {
                CrossfadeTransitionMode.FADE_THROUGH -> fadePlayerVolumeTo(
                    targetVolume = 0f,
                    durationMs = cachedCrossfadeDurationMs
                )
                CrossfadeTransitionMode.DIRECT,
                CrossfadeTransitionMode.FADE_IN_ONLY -> Unit
            }

            if (autoPlay && transitionMode != CrossfadeTransitionMode.DIRECT) {
                setPlayerVolume(0f)
            } else {
                setPlayerVolume(1f)
            }

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (startPositionMs > 0L) {
                exoPlayer.seekTo(startPositionMs)
            }
            if (autoPlay) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }

            if (autoPlay && transitionMode != CrossfadeTransitionMode.DIRECT) {
                fadePlayerVolumeTo(
                    targetVolume = 1f,
                    durationMs = cachedCrossfadeDurationMs
                )
            } else {
                setPlayerVolume(1f)
            }
        } catch (cancelled: CancellationException) {
            setPlayerVolume(1f)
            throw cancelled
        } catch (_: IllegalStateException) {
            setPlayerVolume(1f)
        }
    }

    private suspend fun fadePlayerVolumeTo(targetVolume: Float, durationMs: Int) {
        if (!exoPlayer.isCommandAvailable(Player.COMMAND_SET_VOLUME)) return

        val startVolume = exoPlayer.volume
        val boundedTarget = targetVolume.coerceIn(0f, 1f)
        if (durationMs <= 0 || startVolume == boundedTarget) {
            setPlayerVolume(boundedTarget)
            return
        }

        val steps = (durationMs / 40f).roundToInt().coerceAtLeast(1)
        val delayPerStep = (durationMs / steps.toLong()).coerceAtLeast(1L)
        repeat(steps) { index ->
            val progress = (index + 1) / steps.toFloat()
            val nextVolume = startVolume + (boundedTarget - startVolume) * progress
            setPlayerVolume(nextVolume)
            delay(delayPerStep)
        }
        setPlayerVolume(boundedTarget)
    }

    private fun setPlayerVolume(volume: Float) {
        if (!exoPlayer.isCommandAvailable(Player.COMMAND_SET_VOLUME)) return
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    private fun handlePlaybackEnded() {
        if (sleepTimer.shouldPauseAfterCurrentTrack()) {
            sleepTimer.handleCurrentTrackEnded()
            stateStore.updatePlaying(false)
            return
        }

        val previousIndex = queueManager.currentIndex
        val nextTrack = modeManager.getNextTrack() ?: run {
            stateStore.updatePlaying(false)
            return
        }

        launchTransition {
            when (val result = withContext(Dispatchers.IO) {
                trackPlaybackResolver.resolve(nextTrack, cachedQuality)
            }) {
                is Result.Success -> {
                    val playable = result.data
                    queueManager.updateTrack(queueManager.currentIndex, playable)
                    stateStore.updateTrack(playable)
                    stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
                    stateStore.updatePosition(0L)
                    stateStore.updateDuration(playable.durationMs.coerceAtLeast(0L))
                    loadTrackOnPlayer(
                        track = playable,
                        autoPlay = true,
                        startPositionMs = 0L,
                        transitionMode = if (cachedCrossfadeEnabled) {
                            CrossfadeTransitionMode.FADE_IN_ONLY
                        } else {
                            CrossfadeTransitionMode.DIRECT
                        }
                    )
                    withContext(Dispatchers.IO) {
                        localLibraryRepository.recordRecentPlay(playable)
                    }
                }
                is Result.Error,
                Result.Loading -> {
                    if (previousIndex >= 0) {
                        queueManager.moveToIndex(previousIndex)
                    }
                    stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
                    stateStore.updatePlaying(false)
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            stateStore.updatePlaying(isPlaying)
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            stateStore.updateDuration(exoPlayer.duration.coerceAtLeast(0))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    stateStore.updateDuration(exoPlayer.duration.coerceAtLeast(0))
                    bindEqualizerToAudioSession(exoPlayer.audioSessionId)
                }
                Player.STATE_ENDED -> {
                    handlePlaybackEnded()
                }
                else -> {}
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                cancelActiveTransition()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            stateStore.updateSpeed(playbackParameters.speed)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            bindEqualizerToAudioSession(audioSessionId)
        }
    }

    private enum class CrossfadeTransitionMode {
        DIRECT,
        FADE_IN_ONLY,
        FADE_THROUGH
    }
}
