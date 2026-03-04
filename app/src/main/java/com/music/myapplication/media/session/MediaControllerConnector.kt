package com.music.myapplication.media.session

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
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

    fun playTrack(track: Track, queue: List<Track>, index: Int) {
        queueManager.setQueue(queue, index)
        stateStore.updateTrack(track)
        stateStore.updateQueue(queue, index)
        if (track.playableUrl.isNotBlank()) {
            withController {
                setMediaItem(MediaItem.fromUri(track.playableUrl))
                prepare()
                play()
            }
        }
    }

    fun play() = withController { play() }
    fun pause() = withController { pause() }

    fun seekTo(positionMs: Long) {
        withController { seekTo(positionMs) }
        stateStore.updatePosition(positionMs)
    }

    fun skipToNext(track: Track?) {
        if (track == null) return
        stateStore.updateTrack(track)
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
        if (track.playableUrl.isNotBlank()) {
            withController {
                setMediaItem(MediaItem.fromUri(track.playableUrl))
                prepare()
                play()
            }
        }
    }

    fun skipToPrevious(track: Track?) {
        if (track == null) return
        stateStore.updateTrack(track)
        stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
        if (track.playableUrl.isNotBlank()) {
            withController {
                setMediaItem(MediaItem.fromUri(track.playableUrl))
                prepare()
                play()
            }
        }
    }

    fun stop() {
        withController { stop() }
        stateStore.reset()
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
