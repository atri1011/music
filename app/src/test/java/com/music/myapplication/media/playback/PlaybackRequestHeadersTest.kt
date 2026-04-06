package com.music.myapplication.media.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRequestHeadersTest {

    @Test
    fun normalizePlaybackUrl_upgradesNeteaseCdnHttpToHttps() {
        val result = normalizePlaybackUrl(
            "http://m801.music.126.net/20260406213548/test.mp3"
        )

        assertEquals(
            "https://m801.music.126.net/20260406213548/test.mp3",
            result
        )
    }

    @Test
    fun buildPlaybackRequestHeaders_addsRefererForNeteaseCdn() {
        val result = buildPlaybackRequestHeaders(
            playableUrl = "http://m801.music.126.net/20260406213548/test.mp3"
        )

        assertEquals(PLAYBACK_USER_AGENT, result["User-Agent"])
        assertEquals("https://music.163.com/", result["Referer"])
    }
}
