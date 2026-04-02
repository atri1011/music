package com.music.myapplication.media.state

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackStateStoreTransportTest {

    @Test
    fun `clearPreparedPlaybackTransport resets only transport fields`() {
        val track = Track(
            id = "track-1",
            platform = Platform.QQ,
            title = "晴天",
            artist = "周杰伦",
            playableUrl = "https://example.com/track.mp3",
            durationMs = 245_000L
        )
        val store = PlaybackStateStore().apply {
            updateTrack(track)
            updateQueue(listOf(track), 0)
            updatePosition(4_000L)
            updateDuration(track.durationMs)
            updatePlaying(true)
        }

        store.clearPreparedPlaybackTransport()

        assertEquals(track, store.state.value.currentTrack)
        assertEquals(listOf(track), store.state.value.queue)
        assertEquals(0, store.state.value.currentIndex)
        assertEquals(0L, store.state.value.positionMs)
        assertEquals(0L, store.state.value.durationMs)
        assertFalse(store.state.value.isPlaying)
    }
}
