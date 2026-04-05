package com.music.myapplication.media.session

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.datasource.cache.SimpleCache
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.service.buildPlaybackQueueMediaId
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MediaControllerBindingHarnessTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var playerPreferences: PlayerPreferences

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var stateStore: PlaybackStateStore

    @Inject
    lateinit var playbackCache: SimpleCache

    private lateinit var harness: AndroidTestMediaControllerBindingHarness
    private val tempAudioFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        hiltRule.inject()
        harness = AndroidTestMediaControllerBindingHarness(
            appContext = ApplicationProvider.getApplicationContext<Context>(),
            playerPreferences = playerPreferences,
            queueManager = queueManager,
            stateStore = stateStore,
            playbackCache = playbackCache
        )
        harness.prepare()
    }

    @After
    fun tearDown() {
        harness.cleanup()
        tempAudioFiles.forEach(File::delete)
        tempAudioFiles.clear()
    }

    @Test
    fun buildAsync_binds_to_music_playback_service_and_exposes_custom_commands() {
        harness.connect()

        assertNotNull(harness.withController { connectedToken })
        assertEquals("com.music.myapplication", harness.withController { connectedToken!!.packageName })
        assertTrue(harness.withController { availableSessionCommands.contains(loadTrackSessionCommand) })
        assertTrue(harness.withController { availableSessionCommands.contains(refreshQueueSessionCommand) })
    }

    @Test
    fun refresh_queue_custom_command_updates_service_state() {
        harness.connect()
        val queue = listOf(
            controllerHarnessTrack(id = "1", title = "Alpha"),
            controllerHarnessTrack(id = "2", title = "Beta")
        )

        val result = harness.sendCustomCommand(
            refreshQueueSessionCommand,
            PlaybackQueueRefreshRequest(queue = queue, index = 1).toCommandExtras()
        )

        assertEquals(androidx.media3.session.SessionResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(queue, queueManager.queue)
        assertEquals(1, queueManager.currentIndex)
        assertEquals(queue[1], stateStore.state.value.currentTrack)
        assertEquals(queue, stateStore.state.value.queue)
        assertEquals(1, stateStore.state.value.currentIndex)
    }

    @Test
    fun load_track_custom_command_updates_service_state_and_controller_playlist() {
        harness.connect()
        val queue = listOf(
            controllerHarnessTrack(id = "1", title = "Alpha"),
            controllerHarnessTrack(
                id = "2",
                title = "Beta",
                playableUrl = "content://media/external/audio/media/88"
            )
        )
        val loadedTrack = controllerHarnessTrack(
            id = "2",
            title = "Beta Reloaded",
            playableUrl = "content://media/external/audio/media/88",
            durationMs = 245_000L
        )
        val expectedQueue = listOf(queue[0], loadedTrack)

        val result = harness.sendCustomCommand(
            loadTrackSessionCommand,
            PlaybackLoadRequest(
                track = loadedTrack,
                queue = queue,
                index = 1,
                autoPlay = false,
                startPositionMs = 9_000L
            ).toCommandExtras()
        )

        assertEquals(androidx.media3.session.SessionResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(expectedQueue, queueManager.queue)
        assertEquals(1, queueManager.currentIndex)
        assertEquals(loadedTrack, stateStore.state.value.currentTrack)
        assertEquals(expectedQueue, stateStore.state.value.queue)
        assertEquals(1, stateStore.state.value.currentIndex)
        assertEquals(9_000L, stateStore.state.value.positionMs)
        assertEquals(loadedTrack.durationMs, stateStore.state.value.durationMs)
        assertEquals(false, stateStore.state.value.isPlaying)

        val expectedMediaId = buildPlaybackQueueMediaId(loadedTrack, queueIndex = 1)
        harness.waitUntil("controller playlist update after loadTrack") {
            mediaItemCount == 1 && currentMediaItem?.mediaId == expectedMediaId
        }
        assertEquals(1, harness.withController { mediaItemCount })
        assertEquals(expectedMediaId, harness.withController { currentMediaItem?.mediaId })
    }

    @Test
    fun controller_play_updates_service_and_controller_visible_state() {
        harness.connect()
        val playableFile = writeSilentWaveFile(name = "controller-play-${System.nanoTime()}.wav")
        val track = controllerHarnessTrack(
            id = "play-1",
            title = "Playable",
            playableUrl = playableFile.toURI().toString(),
            durationMs = 5_000L
        )
        val queue = listOf(track)
        val expectedMediaId = buildPlaybackQueueMediaId(track, queueIndex = 0)

        val loadResult = harness.sendCustomCommand(
            loadTrackSessionCommand,
            PlaybackLoadRequest(
                track = track,
                queue = queue,
                index = 0,
                autoPlay = false,
                startPositionMs = 0L
            ).toCommandExtras()
        )

        assertEquals(androidx.media3.session.SessionResult.RESULT_SUCCESS, loadResult.resultCode)
        harness.waitUntil("controller ready after paused local loadTrack") {
            playbackState == Player.STATE_READY &&
                currentMediaItem?.mediaId == expectedMediaId &&
                !playWhenReady
        }
        assertEquals(false, stateStore.state.value.isPlaying)

        harness.play()

        harness.waitUntil("controller play() propagation") {
            playbackState == Player.STATE_READY &&
                currentMediaItem?.mediaId == expectedMediaId &&
                playWhenReady &&
                isPlaying
        }
        assertEquals(expectedMediaId, harness.withController { currentMediaItem?.mediaId })
        assertEquals(true, harness.withController { playWhenReady })
        assertEquals(true, stateStore.state.value.isPlaying)
    }

    private fun writeSilentWaveFile(name: String, durationMs: Long = 5_000L): File {
        val sampleRate = 44_100
        val channels = 1
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val totalSamples = (sampleRate * durationMs / 1_000L).toInt()
        val dataSize = totalSamples * channels * bytesPerSample
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        buffer.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * bytesPerSample)
        buffer.putShort((channels * bytesPerSample).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(dataSize)
        repeat(totalSamples * channels) {
            buffer.putShort(0)
        }

        val file = ApplicationProvider.getApplicationContext<Context>().cacheDir.resolve(name)
        file.writeBytes(buffer.array())
        tempAudioFiles += file
        return file
    }
}
