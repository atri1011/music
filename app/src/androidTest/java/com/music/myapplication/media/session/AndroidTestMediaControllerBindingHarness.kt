package com.music.myapplication.media.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.PlaybackState
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.service.MusicPlaybackService
import com.music.myapplication.media.state.PlaybackStateStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

internal class AndroidTestMediaControllerBindingHarness(
    private val appContext: Context,
    private val playerPreferences: PlayerPreferences,
    private val queueManager: QueueManager,
    private val stateStore: PlaybackStateStore
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun reset() {
        release()
        queueManager.clear()
        stateStore.reset()
        runBlocking {
            playerPreferences.savePlaybackSnapshot(PlaybackState())
            playerPreferences.setPlaybackMode(PlaybackMode.SEQUENTIAL)
            playerPreferences.setCrossfadeEnabled(false)
            playerPreferences.setAutoPlay(true)
        }
    }

    fun connect(): MediaController {
        controllerFuture?.let { return it.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        val sessionToken = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        return future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        appContext.stopService(Intent(appContext, MusicPlaybackService::class.java))
    }

    private companion object {
        const val CONNECTION_TIMEOUT_SECONDS = 10L
    }
}

internal fun controllerHarnessTrack(
    id: String,
    title: String,
    artist: String = "Harness Artist",
    playableUrl: String = "https://example.com/$id.mp3",
    durationMs: Long = 180_000L
): Track = Track(
    id = id,
    platform = Platform.QQ,
    title = title,
    artist = artist,
    album = "Harness Album",
    durationMs = durationMs,
    playableUrl = playableUrl
)
