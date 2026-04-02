package com.music.myapplication.media.service

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.music.myapplication.domain.model.Track

internal data class PlaybackFailureRecoveryRequest(
    val retryKey: String,
    val startPositionMs: Long
)

internal fun buildPlaybackFailureRecoveryRequest(
    track: Track?,
    error: PlaybackException,
    currentPositionMs: Long,
    lastRetryKey: String?
): PlaybackFailureRecoveryRequest? {
    val currentTrack = track ?: return null
    val playableUrl = currentTrack.playableUrl.trim()
    if (!playableUrl.isRemoteHttpUrl()) return null

    val invalidResponse = error.cause as? HttpDataSource.InvalidResponseCodeException ?: return null
    if (!isRetryablePlaybackStatusCode(invalidResponse.responseCode)) return null

    val retryKey = "${currentTrack.platform.id}:${currentTrack.id}:$playableUrl:${invalidResponse.responseCode}"
    if (retryKey == lastRetryKey) return null

    return PlaybackFailureRecoveryRequest(
        retryKey = retryKey,
        startPositionMs = currentPositionMs.coerceAtLeast(0L)
    )
}

private fun isRetryablePlaybackStatusCode(statusCode: Int): Boolean =
    statusCode == 403 || statusCode == 404 || statusCode == 410

private fun String.isRemoteHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
