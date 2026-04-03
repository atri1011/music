package com.music.myapplication.media.service

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GaplessPlaybackWindowTest {

    @Test
    fun `sequential autoplay without crossfade preloads next queue item`() {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B"),
            testTrack(id = "3", title = "C")
        )

        val window = buildGaplessPlaybackWindow(
            queue = queue,
            currentIndex = 1,
            autoPlay = true,
            playbackMode = PlaybackMode.SEQUENTIAL,
            crossfadeEnabled = false
        )

        val actual = requireNotNull(window)
        assertEquals(1, actual.current.queueIndex)
        assertEquals(queue[1], actual.current.track)
        assertNotNull(actual.next)
        assertEquals(2, actual.next?.queueIndex)
        assertEquals(queue[2], actual.next?.track)
    }

    @Test
    fun `crossfade enabled keeps playback window on current item only`() {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B")
        )

        val window = buildGaplessPlaybackWindow(
            queue = queue,
            currentIndex = 0,
            autoPlay = true,
            playbackMode = PlaybackMode.SEQUENTIAL,
            crossfadeEnabled = true
        )

        val actual = requireNotNull(window)
        assertEquals(0, actual.current.queueIndex)
        assertNull(actual.next)
    }

    @Test
    fun `paused prepare keeps current only until playback starts`() {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B")
        )

        val preparedOnly = requireNotNull(
            buildGaplessPlaybackWindow(
                queue = queue,
                currentIndex = 0,
                autoPlay = false,
                playbackMode = PlaybackMode.SEQUENTIAL,
                crossfadeEnabled = false
            )
        )
        val playingWindow = requireNotNull(
            buildGaplessPlaybackWindow(
                queue = queue,
                currentIndex = 0,
                autoPlay = true,
                playbackMode = PlaybackMode.SEQUENTIAL,
                crossfadeEnabled = false
            )
        )

        assertNull(preparedOnly.next)
        assertNotNull(playingWindow.next)
        assertEquals(1, playingWindow.next?.queueIndex)
    }

    @Test
    fun `non sequential mode does not preload next item`() {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B")
        )

        val window = buildGaplessPlaybackWindow(
            queue = queue,
            currentIndex = 0,
            autoPlay = true,
            playbackMode = PlaybackMode.SHUFFLE,
            crossfadeEnabled = false
        )

        val actual = requireNotNull(window)
        assertNull(actual.next)
    }

    @Test
    fun `playback queue media id round trips queue index`() {
        val mediaId = buildPlaybackQueueMediaId(
            track = testTrack(id = "42", title = "Round Trip"),
            queueIndex = 7
        )

        assertEquals(7, playbackQueueIndexFromMediaId(mediaId))
    }

    @Test
    fun `invalid playback queue media id returns null index`() {
        assertNull(playbackQueueIndexFromMediaId("qq:track-1"))
        assertNull(playbackQueueIndexFromMediaId(null))
    }

    private fun testTrack(id: String, title: String) = Track(
        id = id,
        platform = Platform.QQ,
        title = title,
        artist = "歌手"
    )
}
