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

    @Test
    fun `buildPlaybackRequestHeaders keeps existing user agent header even when key casing differs`() {
        val headers = buildPlaybackRequestHeaders(
            playableUrl = "https://cdn.example.com/song.mp3",
            existingHeaders = mapOf("user-agent" to "CustomPlayer/2.0")
        )

        assertEquals("CustomPlayer/2.0", headers["user-agent"])
        assertFalse(headers.containsKey("User-Agent"))
    }

    @Test
    fun `buildPlaybackRequestHeaders keeps existing referer and unrelated request headers`() {
        val headers = buildPlaybackRequestHeaders(
            playableUrl = "https://dl.stream.qqmusic.qq.com/song.mp3",
            existingHeaders = mapOf(
                "referer" to "https://partner.example/",
                "Range" to "bytes=0-"
            )
        )

        assertEquals("https://partner.example/", headers["referer"])
        assertEquals("bytes=0-", headers["Range"])
        assertFalse(headers.containsKey("Referer"))
    }
}
