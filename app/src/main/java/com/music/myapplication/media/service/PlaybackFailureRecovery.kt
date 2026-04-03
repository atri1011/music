package com.music.myapplication.media.service

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.playback.normalizePlaybackUrl
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException

internal enum class PlaybackFailureRecoveryStrategy {
    RELOAD_CURRENT_URL,
    RE_RESOLVE_TRACK
}

internal data class PlaybackFailureRecoveryRequest(
    val retryKey: String,
    val startPositionMs: Long,
    val strategy: PlaybackFailureRecoveryStrategy
)

internal fun buildPlaybackFailureRecoveryRequest(
    track: Track?,
    error: PlaybackException,
    currentPositionMs: Long,
    lastRetryKey: String?
): PlaybackFailureRecoveryRequest? {
    val currentTrack = track ?: return null
    val playableUrl = normalizePlaybackUrl(currentTrack.playableUrl)
    if (!playableUrl.isRemoteHttpUrl()) return null

    val recoveryFingerprint = classifyPlaybackFailure(error) ?: return null
    val retryKey =
        "${currentTrack.platform.id}:${currentTrack.id}:$playableUrl:${recoveryFingerprint.keySuffix}"
    if (retryKey == lastRetryKey) return null

    return PlaybackFailureRecoveryRequest(
        retryKey = retryKey,
        startPositionMs = currentPositionMs.coerceAtLeast(0L),
        strategy = recoveryFingerprint.strategy
    )
}

private data class PlaybackFailureFingerprint(
    val keySuffix: String,
    val strategy: PlaybackFailureRecoveryStrategy
)

private fun classifyPlaybackFailure(error: PlaybackException): PlaybackFailureFingerprint? {
    val cause = error.cause ?: return null
    val invalidResponse = cause as? HttpDataSource.InvalidResponseCodeException
    if (invalidResponse != null) {
        val strategy = when {
            invalidResponse.responseCode in setOf(403, 404, 410) ->
                PlaybackFailureRecoveryStrategy.RE_RESOLVE_TRACK
            invalidResponse.responseCode == 408 ||
                invalidResponse.responseCode == 429 ||
                invalidResponse.responseCode in 500..599 ->
                PlaybackFailureRecoveryStrategy.RELOAD_CURRENT_URL
            else -> null
        } ?: return null
        return PlaybackFailureFingerprint(
            keySuffix = invalidResponse.responseCode.toString(),
            strategy = strategy
        )
    }

    val strategy = when (cause) {
        is SocketTimeoutException -> PlaybackFailureRecoveryStrategy.RELOAD_CURRENT_URL
        is FileNotFoundException -> null
        is IOException -> PlaybackFailureRecoveryStrategy.RELOAD_CURRENT_URL
        else -> null
    } ?: return null
    return PlaybackFailureFingerprint(
        keySuffix = cause::class.java.simpleName.ifBlank { "IOException" },
        strategy = strategy
    )
}

private fun String.isRemoteHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
