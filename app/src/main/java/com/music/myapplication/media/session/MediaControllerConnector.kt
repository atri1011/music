package com.music.myapplication.media.session

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.service.MusicPlaybackService
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControllerConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateStore: PlaybackStateStore,
    private val queueManager: QueueManager
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync().also { future ->
            future.addListener({ /* connected */ }, MoreExecutors.directExecutor())
        }
    }

    fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    fun hasMediaItem(): Boolean {
        val future = controllerFuture ?: return false
        if (!future.isDone) return false
        return runCatching { future.get().mediaItemCount > 0 }.getOrDefault(false)
    }

    fun loadTrack(
        track: Track,
        queue: List<Track>,
        index: Int,
        autoPlay: Boolean,
        startPositionMs: Long = 0L
    ) {
        queueManager.setQueue(queue, index)
        if (queueManager.currentIndex >= 0) {
            queueManager.updateTrack(queueManager.currentIndex, track)
        }
        stateStore.updateTrack(track)
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
        if (track.playableUrl.isNotBlank()) {
            withController {
                sendCustomCommand(
                    loadTrackSessionCommand,
                    PlaybackLoadRequest(
                        track = track,
                        queue = queueManager.queue,
                        index = queueManager.currentIndex,
                        autoPlay = autoPlay,
                        startPositionMs = startPositionMs
                    ).toCommandExtras()
                )
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track>, index: Int, startPositionMs: Long = 0L) {
        loadTrack(
            track = track,
            queue = queue,
            index = index,
            autoPlay = true,
            startPositionMs = startPositionMs
        )
    }

    fun play() = withController { play() }
    fun pause() = withController { pause() }

    fun seekTo(positionMs: Long) {
        withController { seekTo(positionMs) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        withController {
            if (availableCommands.contains(Player.COMMAND_SET_SPEED_AND_PITCH)) {
                setPlaybackSpeed(clamped)
            }
        }
    }

    fun skipToNext(track: Track?) {
        if (track == null) return
        loadTrack(track = track, queue = queueManager.queue, index = queueManager.currentIndex, autoPlay = true)
    }

    fun skipToPrevious(track: Track?) {
        if (track == null) return
        loadTrack(track = track, queue = queueManager.queue, index = queueManager.currentIndex, autoPlay = true)
    }

    fun clearPlayback() {
        withController {
            stop()
            clearMediaItems()
        }
    }

    fun stop() {
        withController { stop() }
    }

    private fun withController(action: MediaController.() -> Unit) {
        val future = controllerFuture ?: return
        if (future.isDone) {
            runCatching { future.get().action() }
            return
        }
        future.addListener(
            { runCatching { future.get().action() } },
            MoreExecutors.directExecutor()
        )
    }
}
