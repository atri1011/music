package com.music.myapplication.data.repository.online

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.extractFirstQqSongId
import com.music.myapplication.data.repository.isDigitsOnly
import com.music.myapplication.data.repository.isLikelySameArtist
import com.music.myapplication.data.repository.isLikelySameTitle
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlin.math.abs

internal class OnlineMusicCommentCandidateResolver(
    private val api: TuneHubApi,
    private val search: suspend (Platform, String, Int, Int) -> Result<List<Track>>
) {
    suspend fun resolveNeteaseCommentSongId(track: Track): String? {
        if (track.platform == Platform.NETEASE && track.id.isDigitsOnly()) return track.id
        return findCommentTrackCandidate(track, Platform.NETEASE)
            ?.id
            ?.takeIf { it.isDigitsOnly() }
    }

    suspend fun resolveQqCommentSongId(track: Track): String? {
        if (track.platform == Platform.QQ) {
            resolveQqSongId(track.id)?.let { return it }
        }

        val candidate = findCommentTrackCandidate(track, Platform.QQ) ?: return null
        return resolveQqSongId(candidate.id)
    }

    suspend fun findQqTrackCandidate(track: Track): Track? {
        return findCommentTrackCandidate(track, Platform.QQ)
    }

    private suspend fun findCommentTrackCandidate(track: Track, platform: Platform): Track? {
        val keyword = listOf(track.title, track.artist)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val searchResult = search(
            platform,
            keyword,
            1,
            20
        )
        val candidates = (searchResult as? Result.Success)?.data.orEmpty()
            .filter { it.id.isNotBlank() }
        return selectBestMatchedTrack(reference = track, candidates = candidates)
    }

    private suspend fun resolveQqSongId(songIdOrMid: String): String? {
        if (songIdOrMid.isBlank()) return null
        if (songIdOrMid.isDigitsOnly()) return songIdOrMid

        return runCatching {
            api.getQqSongDetail(songMid = songIdOrMid)
        }.getOrNull()
            ?.let(::extractFirstQqSongId)
    }

    private fun selectBestMatchedTrack(reference: Track, candidates: List<Track>): Track? {
        if (candidates.isEmpty()) return null

        val titleAndArtistMatches = candidates.filter { candidate ->
            candidate.title.isLikelySameTitle(reference.title) &&
                candidate.artist.isLikelySameArtist(reference.artist)
        }
        val titleMatches = candidates.filter { candidate ->
            candidate.title.isLikelySameTitle(reference.title)
        }
        val durationMatched = titleAndArtistMatches.firstOrNull { candidate ->
            candidate.durationMs.isCloseTo(reference.durationMs)
        }

        return durationMatched
            ?: titleAndArtistMatches.firstOrNull()
            ?: titleMatches.firstOrNull { candidate -> candidate.durationMs.isCloseTo(reference.durationMs) }
            ?: titleMatches.firstOrNull()
            ?: candidates.firstOrNull { candidate -> candidate.artist.isLikelySameArtist(reference.artist) }
            ?: candidates.firstOrNull()
    }

    private fun Long.isCloseTo(other: Long): Boolean {
        if (this <= 0L || other <= 0L) return true
        return abs(this - other) <= 5_000L
    }
}
