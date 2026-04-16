package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.data.repository.PlaybackSourceRouter
import com.music.myapplication.data.repository.isLikelySameArtist
import com.music.myapplication.data.repository.isLikelySameTitle
import com.music.myapplication.domain.model.AudioSource
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import java.io.File
import java.net.URI
import javax.inject.Inject

data class ResolvedTrackPlayback(
    val track: Track,
    val sourceFallbackMessage: String? = null
)

class TrackPlaybackResolver @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val sourceRouter: PlaybackSourceRouter,
    private val downloadManager: DownloadManager
) {
    suspend fun resolve(track: Track, quality: String): Result<ResolvedTrackPlayback> {
        resolveLocalPlayableTrack(track)?.let {
            return Result.Success(ResolvedTrackPlayback(track = it))
        }

        val requestedSource = sourceRouter.currentRequestedSource()
        val attemptedTrackKeys = LinkedHashSet<String>()
        var lastError: AppError? = null

        requestedSource.supportedPlatforms.forEach { platform ->
            if (platform == track.platform) {
                when (val attempt = tryResolveCandidate(
                    originalTrack = track,
                    candidate = track,
                    quality = quality,
                    requestedSource = requestedSource,
                    attemptedTrackKeys = attemptedTrackKeys
                )) {
                    is Result.Success -> return attempt
                    is Result.Error -> lastError = attempt.error
                    null,
                    Result.Loading -> Unit
                }
            }
            if (shouldSearchCandidate(track, platform)) {
                findSearchCandidate(track, platform)?.let { candidate ->
                    when (val attempt = tryResolveCandidate(
                        originalTrack = track,
                        candidate = candidate,
                        quality = quality,
                        requestedSource = requestedSource,
                        attemptedTrackKeys = attemptedTrackKeys
                    )) {
                        is Result.Success -> return attempt
                        is Result.Error -> lastError = attempt.error
                        null,
                        Result.Loading -> Unit
                    }
                }
            }
        }

        if (!requestedSource.supportedPlatforms.contains(track.platform)) {
            when (val attempt = tryResolveCandidate(
                originalTrack = track,
                candidate = track,
                quality = quality,
                requestedSource = requestedSource,
                attemptedTrackKeys = attemptedTrackKeys
            )) {
                is Result.Success -> return attempt
                is Result.Error -> lastError = attempt.error
                null,
                Result.Loading -> Unit
            }
        }

        return Result.Error(lastError ?: AppError.Parse(message = "解析播放地址失败"))
    }

    private suspend fun resolveLocalPlayableTrack(track: Track): Track? {
        if (track.platform != Platform.LOCAL && track.playableUrl.startsWith("content://", ignoreCase = true)) {
            val downloadedUri = downloadManager.getDownloadedFilePath(track.id, track.platform.id)
            return downloadedUri?.let { track.copy(playableUrl = it) }
        }

        track.playableUrl.toLocalPlayableUriOrNull()?.let { localUri ->
            return track.copy(playableUrl = localUri)
        }

        val downloadedPath = downloadManager.getDownloadedFilePath(track.id, track.platform.id)
        val localUri = downloadedPath?.toLocalPlayableUriOrNull() ?: return null
        return track.copy(playableUrl = localUri)
    }

    private suspend fun findSearchCandidate(track: Track, platform: Platform): Track? {
        val keyword = listOf(track.title, track.artist)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val searchResult = onlineRepo.search(
            platform = platform,
            keyword = keyword,
            page = 1,
            pageSize = 20
        )
        val candidates = (searchResult as? Result.Success)?.data.orEmpty()
            .filter { it.id.isNotBlank() }
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            candidate.title.isLikelySameTitle(track.title) &&
                candidate.artist.isLikelySameArtist(track.artist)
        } ?: candidates.firstOrNull { candidate ->
            candidate.title.isLikelySameTitle(track.title)
        } ?: candidates.firstOrNull()
    }

    private fun shouldSearchCandidate(track: Track, targetPlatform: Platform): Boolean {
        val hasKeyword = track.title.isNotBlank() || track.artist.isNotBlank()
        if (!hasKeyword) return false
        return targetPlatform != track.platform || track.id.isBlank() || track.id.isDigitsOnly()
    }

    private suspend fun tryResolveCandidate(
        originalTrack: Track,
        candidate: Track,
        quality: String,
        requestedSource: AudioSource,
        attemptedTrackKeys: MutableSet<String>
    ): Result<ResolvedTrackPlayback>? {
        if (!attemptedTrackKeys.add(candidate.trackKey())) return null
        return when (val result = sourceRouter.resolve(candidate, quality, requestedSource)) {
            is Result.Success -> Result.Success(
                ResolvedTrackPlayback(
                    track = mergeSessionTrack(
                        originalTrack = originalTrack,
                        resolvedTrack = candidate,
                        playableUrl = result.data.playableUrl,
                        quality = quality
                    ),
                    sourceFallbackMessage = result.data.fallbackReason
                        ?.takeIf { result.data.didFallback }
                )
            )
            is Result.Error -> Result.Error(result.error)
            Result.Loading -> null
        }
    }

    private fun mergeSessionTrack(
        originalTrack: Track,
        resolvedTrack: Track,
        playableUrl: String,
        quality: String
    ): Track {
        return originalTrack.copy(
            platform = resolvedTrack.platform,
            id = resolvedTrack.id,
            title = originalTrack.title.ifBlank { resolvedTrack.title },
            artist = originalTrack.artist.ifBlank { resolvedTrack.artist },
            album = originalTrack.album.ifBlank { resolvedTrack.album },
            albumId = originalTrack.albumId.ifBlank { resolvedTrack.albumId },
            coverUrl = originalTrack.coverUrl.ifBlank { resolvedTrack.coverUrl },
            durationMs = originalTrack.durationMs.takeIf { it > 0L } ?: resolvedTrack.durationMs,
            playableUrl = playableUrl,
            quality = quality
        )
    }
}

private fun String.toLocalPlayableUriOrNull(): String? {
    val value = trim()
    if (value.isBlank()) return null

    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> null

        value.startsWith("content://", ignoreCase = true) -> value

        value.startsWith("file://", ignoreCase = true) -> {
            val path = runCatching { URI.create(value).path }.getOrNull().orEmpty()
            if (path.isNotBlank() && File(path).exists()) value else null
        }

        else -> {
            val file = File(value)
            if (file.exists()) file.toURI().toString() else null
        }
    }
}

private fun String.isDigitsOnly(): Boolean = isNotBlank() && all { it.isDigit() }

private fun Track.trackKey(): String = "${platform.id}:$id"
