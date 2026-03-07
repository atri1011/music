package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository
) : RecommendationRepository {

    override suspend fun getDailyRecommendedTracks(limit: Int): List<Track> {
        // Get the first toplist from Netease and return its tracks
        val toplists = when (val result = onlineRepo.getToplists(Platform.NETEASE)) {
            is Result.Success -> result.data
            else -> return emptyList()
        }
        val firstId = toplists.firstOrNull()?.id ?: return emptyList()
        return when (val detail = onlineRepo.getToplistDetail(Platform.NETEASE, firstId)) {
            is Result.Success -> detail.data.take(limit)
            else -> emptyList()
        }
    }

    override suspend fun getFmTrack(): Track? {
        // Pick a random track from recent plays or favorites
        val randomRecent = localRepo.getRandomRecentTrack()
        if (randomRecent != null) return randomRecent
        // Fallback to first favorite
        return localRepo.getFavorites().firstOrNull()?.firstOrNull()
    }

    override suspend fun getRecommendedPlaylists(): List<ToplistInfo> {
        return when (val result = onlineRepo.getToplists(Platform.NETEASE)) {
            is Result.Success -> result.data.take(6)
            else -> emptyList()
        }
    }

    override suspend fun getSimilarTracks(track: Track, limit: Int): List<Track> {
        // Search by artist name to find similar tracks
        val keyword = track.artist.trim()
        if (keyword.isBlank()) return emptyList()
        return when (val result = onlineRepo.search(track.platform, keyword, page = 1, pageSize = limit + 5)) {
            is Result.Success -> result.data
                .filter { it.id != track.id }
                .take(limit)
            else -> emptyList()
        }
    }
}
