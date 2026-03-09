package com.music.myapplication.media.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var stateStore: PlaybackStateStore
    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var modeManager: PlaybackModeManager
    @Inject lateinit var sleepTimer: SleepTimerStateHolder

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null

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

        mediaSession = MediaSession.Builder(this, exoPlayer).build()
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
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
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
                }
                Player.STATE_ENDED -> {
                    if (sleepTimer.shouldPauseAfterCurrentTrack()) {
                        sleepTimer.handleCurrentTrackEnded()
                        stateStore.updatePlaying(false)
                        return
                    }
                    val next = modeManager.getNextTrack()
                    if (next != null && next.playableUrl.isNotBlank()) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(next.playableUrl))
                        exoPlayer.prepare()
                        exoPlayer.play()
                        stateStore.updateTrack(next)
                        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
                    } else {
                        stateStore.updatePlaying(false)
                    }
                }
                else -> {}
            }
        }
    }
}
