package com.music.myapplication.media.service

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFailureRecoveryTest {

    @Test
    fun buildsRecoveryRequestForSupportedHttpStatus() {
        val track = remoteTrack(playableUrl = "https://cdn.example.com/old.mp3")

        val request = buildPlaybackFailureRecoveryRequest(
            track = track,
            error = playbackExceptionForStatus(403),
            currentPositionMs = 42_000L,
            lastRetryKey = null
        )

        assertNotNull(request)
        assertEquals(42_000L, request?.startPositionMs)
        assertEquals("qq:song-1:https://cdn.example.com/old.mp3:403", request?.retryKey)
    }

    @Test
    fun skipsRecoveryForUnsupportedStatusOrLocalTrack() {
        val remoteTrack = remoteTrack(playableUrl = "https://cdn.example.com/old.mp3")
        val localTrack = remoteTrack(playableUrl = "content://media/external/audio/media/1")

        assertNull(
            buildPlaybackFailureRecoveryRequest(
                track = remoteTrack,
                error = playbackExceptionForStatus(500),
                currentPositionMs = 1_000L,
                lastRetryKey = null
            )
        )
        assertNull(
            buildPlaybackFailureRecoveryRequest(
                track = localTrack,
                error = playbackExceptionForStatus(403),
                currentPositionMs = 1_000L,
                lastRetryKey = null
            )
        )
    }

    @Test
    fun skipsRecoveryWhenSameFailureWasAlreadyRetried() {
        val track = remoteTrack(playableUrl = "https://cdn.example.com/old.mp3")
        val retryKey = "qq:song-1:https://cdn.example.com/old.mp3:404"

        val request = buildPlaybackFailureRecoveryRequest(
            track = track,
            error = playbackExceptionForStatus(404),
            currentPositionMs = 2_000L,
            lastRetryKey = retryKey
        )

        assertNull(request)
    }

    private fun playbackExceptionForStatus(statusCode: Int): PlaybackException {
        val cause = HttpDataSource.InvalidResponseCodeException(
            statusCode,
            null,
            IOException("HTTP $statusCode"),
            emptyMap(),
            mockk(relaxed = true),
            ByteArray(0)
        )
        return mockk {
            every { this@mockk.cause } returns cause
        }
    }

    private fun remoteTrack(playableUrl: String) = Track(
        id = "song-1",
        platform = Platform.QQ,
        title = "晴天",
        artist = "周杰伦",
        playableUrl = playableUrl
    )
}
