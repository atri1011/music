package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.data.repository.PlaybackSourceRouter
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import javax.inject.Inject
import java.io.File
import java.net.URI

class TrackPlaybackResolver @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val sourceRouter: PlaybackSourceRouter,
    private val downloadManager: DownloadManager
) {
    suspend fun resolve(track: Track, quality: String): Result<Track> {
        resolveLocalPlayableTrack(track)?.let { return Result.Success(it) }

        when (val result = sourceRouter.resolve(track, quality)) {
            is Result.Success -> {
                return Result.Success(track.copy(playableUrl = result.data, quality = quality))
            }
            is Result.Error -> {
                if (!shouldTryQqFallback(track)) {
                    return Result.Error(result.error)
                }
                return tryQqFallback(track, quality, result.error)
            }
            Result.Loading -> return Result.Loading
        }
    }

    private suspend fun resolveLocalPlayableTrack(track: Track): Track? {
        track.playableUrl.toLocalPlayableUriOrNull()?.let { localUri ->
            return track.copy(playableUrl = localUri)
        }

        val downloadedPath = downloadManager.getDownloadedFilePath(track.id, track.platform.id)
        val localUri = downloadedPath?.toLocalPlayableUriOrNull() ?: return null
        return track.copy(playableUrl = localUri)
    }

    private suspend fun findQqMidCandidate(track: Track): Track? {
        val keyword = listOf(track.title, track.artist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val searchResult = onlineRepo.search(
            platform = Platform.QQ,
            keyword = keyword,
            page = 1,
            pageSize = 20
        )
        val candidates = (searchResult as? Result.Success)?.data.orEmpty()
            .filter { it.id.isNotBlank() && !it.id.isDigitsOnly() }
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            candidate.title.equals(track.title, ignoreCase = true) &&
                candidate.artist.isLikelySameArtist(track.artist)
        } ?: candidates.firstOrNull { candidate ->
            candidate.title.equals(track.title, ignoreCase = true)
        } ?: candidates.firstOrNull()
    }

    private suspend fun tryQqFallback(
        track: Track,
        quality: String,
        originalError: AppError
    ): Result<Track> {
        val candidate = findQqMidCandidate(track) ?: return Result.Error(originalError)
        return when (val retry = onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, quality)) {
            is Result.Success -> Result.Success(
                track.copy(
                    platform = Platform.QQ,
                    id = candidate.id,
                    title = if (track.title.isBlank()) candidate.title else track.title,
                    artist = if (track.artist.isBlank()) candidate.artist else track.artist,
                    album = if (track.album.isBlank()) candidate.album else track.album,
                    coverUrl = if (track.coverUrl.isBlank()) candidate.coverUrl else track.coverUrl,
                    durationMs = if (track.durationMs <= 0L) candidate.durationMs else track.durationMs,
                    playableUrl = retry.data,
                    quality = quality
                )
            )
            is Result.Error -> Result.Error(retry.error)
            Result.Loading -> Result.Error(originalError)
        }
    }

    private fun shouldTryQqFallback(track: Track): Boolean {
        if (!track.id.isDigitsOnly()) return false
        return when (track.platform) {
            Platform.QQ -> true
            Platform.NETEASE -> track.title.isNotBlank() && track.artist.isNotBlank()
            else -> false
        }
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

private fun String.isLikelySameArtist(other: String): Boolean {
    if (isBlank() || other.isBlank()) return true
    val left = lowercase().replace(" ", "")
    val right = other.lowercase().replace(" ", "")
    if (left.contains(right) || right.contains(left)) return true
    val leftTokens = left.split(Regex("[,，/&、]")).map { it.trim() }.filter { it.isNotBlank() }
    val rightTokens = right.split(Regex("[,，/&、]")).map { it.trim() }.filter { it.isNotBlank() }
    return leftTokens.any { token -> rightTokens.any { it == token || it.contains(token) || token.contains(it) } }
}
