package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.Result
import com.music.myapplication.data.repository.PlaybackSourceRouter
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import javax.inject.Inject

class TrackPlaybackResolver @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val sourceRouter: PlaybackSourceRouter
) {
    suspend fun resolve(track: Track, quality: String): Result<Track> {
        when (val result = sourceRouter.resolve(track, quality)) {
            is Result.Success -> {
                return Result.Success(track.copy(playableUrl = result.data, quality = quality))
            }
            is Result.Error -> {
                if (track.platform != Platform.QQ || !track.id.isDigitsOnly()) {
                    return Result.Error(result.error)
                }

                val candidate = findQqMidCandidate(track) ?: return Result.Error(result.error)
                return when (val retry = onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, quality)) {
                    is Result.Success -> Result.Success(
                        track.copy(
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
                    Result.Loading -> Result.Error(result.error)
                }
            }
            Result.Loading -> return Result.Loading
        }
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
