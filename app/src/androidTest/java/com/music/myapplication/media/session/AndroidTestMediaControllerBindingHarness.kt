package com.music.myapplication.media.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

internal class AndroidTestMediaControllerBindingHarness(
    private val appContext: Context,
    private val playerPreferences: PlayerPreferences,
    private val queueManager: QueueManager,
    private val stateStore: PlaybackStateStore,
    private val playbackCache: SimpleCache
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun prepare() {
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

    fun cleanup() {
        prepare()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        playbackCache.release()
    }

    fun connect(): MediaController {
        controllerFuture?.let { return it.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        val sessionToken = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future = runOnControllerThread {
            MediaController.Builder(appContext, sessionToken).buildAsync()
        }
        controllerFuture = future
        return future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun sendCustomCommand(command: SessionCommand, args: Bundle): SessionResult {
        val future = withController { sendCustomCommand(command, args) }
        return future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun <T> withController(action: MediaController.() -> T): T {
        val controller = connect()
        return runOnControllerThread { controller.action() }
    }

    fun waitUntil(
        description: String,
        timeoutMs: Long = CONDITION_TIMEOUT_MS,
        condition: MediaController.() -> Boolean
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (withController(condition)) return
            SystemClock.sleep(CONDITION_POLL_INTERVAL_MS)
        }
        instrumentation.waitForIdleSync()
        if (!withController(condition)) {
            throw AssertionError("$description was not observed within ${timeoutMs}ms")
        }
    }

    fun release() {
        controllerFuture?.let { future ->
            runOnControllerThread {
                MediaController.releaseFuture(future)
            }
        }
        controllerFuture = null
        appContext.stopService(Intent(appContext, MusicPlaybackService::class.java))
    }

    private fun <T> runOnControllerThread(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return action()
        }
        val resultRef = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resultRef.set(runCatching { action() })
        }
        return resultRef.get().getOrThrow()
    }

    private companion object {
        const val CONNECTION_TIMEOUT_SECONDS = 10L
        const val CONDITION_TIMEOUT_MS = 5_000L
        const val CONDITION_POLL_INTERVAL_MS = 50L
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
