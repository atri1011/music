package com.music.myapplication.media.session

import android.content.Context
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.state.PlaybackStateStore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerConnectorTest {

    @Test
    fun `seekTo without connected controller leaves position untouched`() {
        val stateStore = PlaybackStateStore().apply { updatePosition(1_000L) }
        val connector = MediaControllerConnector(
            context = mockk<Context>(relaxed = true),
            stateStore = stateStore,
            queueManager = QueueManager()
        )

        connector.seekTo(9_000L)

        assertEquals(1_000L, stateStore.state.value.positionMs)
    }

    @Test
    fun `stop without connected controller leaves playback state untouched`() {
        val track = testTrack(playableUrl = "https://example.com/current.mp3", durationMs = 245_000L)
        val stateStore = PlaybackStateStore().apply {
            updateTrack(track)
            updateQueue(listOf(track), 0)
            updatePosition(4_000L)
            updateDuration(track.durationMs)
            updatePlaying(true)
        }
        val connector = MediaControllerConnector(
            context = mockk<Context>(relaxed = true),
            stateStore = stateStore,
            queueManager = QueueManager()
        )

        connector.stop()

        assertEquals(track, stateStore.state.value.currentTrack)
        assertEquals(listOf(track), stateStore.state.value.queue)
        assertEquals(0, stateStore.state.value.currentIndex)
        assertEquals(4_000L, stateStore.state.value.positionMs)
        assertEquals(track.durationMs, stateStore.state.value.durationMs)
        assertTrue(stateStore.state.value.isPlaying)
    }

    @Test
    fun `loadTrack without connected controller keeps transport state service driven`() {
        val track = testTrack(playableUrl = "https://example.com/loaded.mp3", durationMs = 245_000L)
        val queueManager = QueueManager()
        val stateStore = PlaybackStateStore().apply {
            updatePosition(1_000L)
            updateDuration(2_000L)
            updatePlaying(true)
        }
        val connector = MediaControllerConnector(
            context = mockk<Context>(relaxed = true),
            stateStore = stateStore,
            queueManager = queueManager
        )

        connector.loadTrack(
            track = track,
            queue = listOf(track),
            index = 0,
            autoPlay = false,
            startPositionMs = 9_000L
        )

        assertEquals(track, stateStore.state.value.currentTrack)
        assertEquals(listOf(track), stateStore.state.value.queue)
        assertEquals(0, stateStore.state.value.currentIndex)
        assertEquals(1_000L, stateStore.state.value.positionMs)
        assertEquals(2_000L, stateStore.state.value.durationMs)
        assertTrue(stateStore.state.value.isPlaying)
    }

    @Test
    fun `clearPlayback without connected controller leaves transport state untouched`() {
        val track = testTrack(playableUrl = "https://example.com/loaded.mp3", durationMs = 245_000L)
        val queueManager = QueueManager().apply { setQueue(listOf(track), 0) }
        val stateStore = PlaybackStateStore().apply {
            updateTrack(track)
            updateQueue(listOf(track), 0)
            updatePosition(3_000L)
            updateDuration(track.durationMs)
            updatePlaying(true)
        }
        val connector = MediaControllerConnector(
            context = mockk<Context>(relaxed = true),
            stateStore = stateStore,
            queueManager = queueManager
        )

        connector.clearPlayback()

        assertEquals(track, stateStore.state.value.currentTrack)
        assertEquals(listOf(track), stateStore.state.value.queue)
        assertEquals(0, stateStore.state.value.currentIndex)
        assertEquals(3_000L, stateStore.state.value.positionMs)
        assertEquals(track.durationMs, stateStore.state.value.durationMs)
        assertTrue(stateStore.state.value.isPlaying)
    }

    private fun testTrack(
        id: String = "track-1",
        playableUrl: String = "",
        durationMs: Long = 245_000L
    ) = Track(
        id = id,
        platform = Platform.QQ,
        title = "晴天",
        artist = "周杰伦",
        playableUrl = playableUrl,
        durationMs = durationMs
    )
}
