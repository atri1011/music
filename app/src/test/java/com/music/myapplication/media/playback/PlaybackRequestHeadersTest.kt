package com.music.myapplication.media.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackRequestHeadersTest {

    @Test
    fun `buildPlaybackRequestHeaders always includes player user agent`() {
        val headers = buildPlaybackRequestHeaders("https://cdn.example.com/song.mp3")

        assertEquals(PLAYBACK_USER_AGENT, headers["User-Agent"])
    }

    @Test
    fun `buildPlaybackRequestHeaders adds qq referer for qq playback urls`() {
        val headers = buildPlaybackRequestHeaders("https://dl.stream.qqmusic.qq.com/song.mp3")

        assertEquals("https://y.qq.com/", headers["Referer"])
    }

    @Test
    fun `buildPlaybackRequestHeaders skips referer for unrelated hosts`() {
        val headers = buildPlaybackRequestHeaders("https://cdn.example.com/song.mp3")

        assertFalse(headers.containsKey("Referer"))
    }
}
