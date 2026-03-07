package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val api: TuneHubApi,
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
        val response = runCatching {
            api.getNeteaseRecommendedPlaylists(limit = RECOMMENDED_PLAYLIST_LIMIT)
        }.getOrElse { return emptyList() }

        return extractNeteaseRecommendedPlaylists(response)
            .take(RECOMMENDED_PLAYLIST_LIMIT)
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

    private companion object {
        const val RECOMMENDED_PLAYLIST_LIMIT = 6
    }
}

internal fun extractNeteaseRecommendedPlaylists(data: JsonElement?): List<ToplistInfo> {
    val root = data as? JsonObject ?: return emptyList()
    val code = (root["code"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    if (code != null && code != 200) return emptyList()

    val items = root["result"] as? JsonArray ?: return emptyList()
    return items.mapNotNull { itemElement ->
        val item = itemElement as? JsonObject ?: return@mapNotNull null
        val id = item.firstStringOf("id").orEmpty()
        val name = item.firstStringOf("name").orEmpty()
        if (id.isBlank() || name.isBlank()) return@mapNotNull null

        ToplistInfo(
            id = id,
            name = name,
            coverUrl = item.firstStringOf("picUrl").orEmpty(),
            description = item.firstStringOf("copywriter").orEmpty()
        )
    }
}

private fun JsonObject.firstStringOf(vararg keys: String): String? {
    keys.forEach { key ->
        val value = (this[key] as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}
