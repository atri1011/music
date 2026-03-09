package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.GuessYouLikeResult
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.ToplistInfo
import java.util.Random
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
    private val localRepo: LocalLibraryRepository,
    private val homeContentCacheStore: HomeContentCacheStore
) : RecommendationRepository {

    override suspend fun getDailyRecommendedTracks(limit: Int): List<Track> {
        val favorites = localRepo.getFavorites().firstOrNull().orEmpty()
        val recentTracks = localRepo.getRecentPlays(limit = RECENT_HISTORY_LIMIT).firstOrNull().orEmpty()
        val tasteSeeds = buildTasteSeeds(favorites, recentTracks)
        if (tasteSeeds.isEmpty()) {
            return getColdStartTracks(limit)
        }

        val knownTrackKeys = (favorites + recentTracks)
            .map { it.uniqueKey() }
            .toHashSet()
        val recommended = linkedMapOf<String, Track>()

        for (seed in tasteSeeds.take(MAX_PERSONALIZED_SEEDS)) {
            if (recommended.size >= limit) break
            val similarTracks = getSimilarTracks(seed.track, limit = PERSONALIZED_SIMILAR_LIMIT)
            for (candidate in similarTracks) {
                val key = candidate.uniqueKey()
                if (key in knownTrackKeys || key in recommended) continue
                recommended[key] = candidate
                if (recommended.size >= limit) break
            }
        }

        if (recommended.size < limit) {
            for (seed in tasteSeeds) {
                val key = seed.track.uniqueKey()
                if (key in recommended) continue
                recommended[key] = seed.track
                if (recommended.size >= limit) break
            }
        }

        if (recommended.isNotEmpty()) {
            return localRepo.applyFavoriteState(recommended.values.take(limit))
        }

        return tasteSeeds
            .map { it.track }
            .take(limit)
    }

    override suspend fun getFmTrack(): Track? {
        // Pick a random track from recent plays or favorites
        val randomRecent = localRepo.getRandomRecentTrack()
        if (randomRecent != null) return randomRecent
        // Fallback to first favorite
        return localRepo.getFavorites().firstOrNull()?.firstOrNull()
    }

    override suspend fun getRecommendedPlaylists(): List<ToplistInfo> {
        homeContentCacheStore.getCachedRecommendedPlaylists()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val response = runCatching {
            api.getNeteaseRecommendedPlaylists(limit = RECOMMENDED_PLAYLIST_LIMIT)
        }.getOrElse { return emptyList() }

        val playlists = extractNeteaseRecommendedPlaylists(response)
            .take(RECOMMENDED_PLAYLIST_LIMIT)
        if (playlists.isNotEmpty()) {
            homeContentCacheStore.cacheRecommendedPlaylists(playlists)
        }
        return playlists
    }

    override suspend fun getGuessYouLikeTracks(refreshCount: Int, limit: Int): GuessYouLikeResult {
        val favorites = localRepo.getFavorites().firstOrNull().orEmpty()
        val recentTracks = localRepo.getRecentPlays(limit = GUESS_HISTORY_LIMIT).firstOrNull().orEmpty()

        if (favorites.isEmpty() && recentTracks.isEmpty()) {
            return GuessYouLikeResult("热门推荐", getColdStartTracks(limit, refreshCount))
        }

        val tasteSeeds = buildTasteSeeds(favorites, recentTracks)
        if (tasteSeeds.isEmpty()) {
            return GuessYouLikeResult("热门推荐", getColdStartTracks(limit, refreshCount))
        }

        val artistScores = mutableMapOf<String, Double>()
        val artistPlatform = mutableMapOf<String, Platform>()
        for (seed in tasteSeeds) {
            val artist = seed.track.artist.asRecommendationKeyword()
            if (artist.isBlank()) continue
            artistScores[artist] = (artistScores[artist] ?: 0.0) + seed.score
            if (artist !in artistPlatform) artistPlatform[artist] = seed.track.platform
        }
        if (artistScores.isEmpty()) {
            return GuessYouLikeResult("热门推荐", getColdStartTracks(limit, refreshCount))
        }

        val rankedArtists = artistScores.entries.sortedByDescending { it.value }.map { it.key }
        val shuffled = rankedArtists.toMutableList().apply { shuffle(Random(refreshCount.toLong())) }
        val seedArtists = shuffled.take(GUESS_SEED_ARTIST_COUNT)

        val knownKeys = (favorites + recentTracks).map { it.uniqueKey() }.toHashSet()
        val recommended = linkedMapOf<String, Track>()
        val strategies = listOf("", " 热门", " 新歌")

        for (artist in seedArtists) {
            if (recommended.size >= limit) break
            val suffix = strategies[refreshCount % strategies.size]
            val query = "$artist$suffix"
            val page = 1 + (refreshCount / strategies.size) % 3
            val platform = artistPlatform[artist] ?: Platform.NETEASE

            val result = onlineRepo.search(platform, query, page = page, pageSize = limit + 5)
            if (result is Result.Success) {
                for (track in result.data) {
                    if (track.title.isBlank() || track.artist.isBlank()) continue
                    val key = track.uniqueKey()
                    if (key in knownKeys || key in recommended) continue
                    recommended[key] = track
                    if (recommended.size >= limit) break
                }
            }
        }

        if (recommended.size < limit / 2) {
            val titleSeed = tasteSeeds[refreshCount % tasteSeeds.size].track.title
            val result = onlineRepo.search(Platform.NETEASE, titleSeed, page = 1, pageSize = limit)
            if (result is Result.Success) {
                for (track in result.data) {
                    if (track.title.isBlank() || track.artist.isBlank()) continue
                    val key = track.uniqueKey()
                    if (key in knownKeys || key in recommended) continue
                    recommended[key] = track
                    if (recommended.size >= limit) break
                }
            }
        }

        if (recommended.isNotEmpty()) {
            return GuessYouLikeResult(
                label = seedArtists.firstOrNull() ?: "热门推荐",
                tracks = localRepo.applyFavoriteState(recommended.values.take(limit))
            )
        }

        return GuessYouLikeResult(
            label = "热门推荐",
            tracks = getColdStartTracks(limit, refreshCount)
        )
    }

    override suspend fun getSimilarTracks(track: Track, limit: Int): List<Track> {
        val keyword = track.artist.asRecommendationKeyword()
        if (keyword.isBlank()) return emptyList()
        return when (val result = onlineRepo.search(track.platform, keyword, page = 1, pageSize = limit + 5)) {
            is Result.Success -> result.data
                .filter { it.id != track.id }
                .take(limit)
            else -> emptyList()
        }
    }

    private suspend fun buildTasteSeeds(
        favorites: List<Track>,
        recentTracks: List<Track>
    ): List<TasteSeed> {
        val favoriteRanks = favorites
            .take(FAVORITE_SEED_LIMIT)
            .withIndex()
            .associate { it.value.uniqueKey() to it.index }
        val recentRanks = recentTracks
            .take(RECENT_SEED_LIMIT)
            .withIndex()
            .associate { it.value.uniqueKey() to it.index }
        val candidates = (favorites.take(FAVORITE_SEED_LIMIT) + recentTracks.take(RECENT_SEED_LIMIT))
            .distinctBy { it.uniqueKey() }
        if (candidates.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        return candidates.map { track ->
            val key = track.uniqueKey()
            val favoriteScore = favoriteRanks[key]
                ?.let { FAVORITE_SCORE_BASE - (it * FAVORITE_SCORE_DECAY) }
                ?.coerceAtLeast(FAVORITE_SCORE_FLOOR)
                ?: 0.0
            val recentScore = recentRanks[key]
                ?.let { RECENT_SCORE_BASE - (it * RECENT_SCORE_DECAY) }
                ?.coerceAtLeast(RECENT_SCORE_FLOOR)
                ?: 0.0
            val playCount = localRepo.getTrackPlayCount(track.id, track.platform.id)
            val playCountScore = playCount.coerceAtMost(MAX_PLAYCOUNT_SCORE).toDouble()
            val firstPlayDate = localRepo.getFirstPlayDate(track.id, track.platform.id)
            val loyaltyScore = when {
                firstPlayDate == null -> 0.0
                now - firstPlayDate >= LONG_TERM_LISTENING_MS -> LONG_TERM_LISTENING_BONUS
                now - firstPlayDate >= MID_TERM_LISTENING_MS -> MID_TERM_LISTENING_BONUS
                else -> 0.0
            }
            TasteSeed(
                track = track.copy(isFavorite = track.isFavorite || key in favoriteRanks),
                score = favoriteScore + recentScore + playCountScore + loyaltyScore
            )
        }.sortedByDescending { it.score }
    }

    private suspend fun getColdStartTracks(limit: Int, refreshCount: Int = 0): List<Track> {
        val toplists = when (val result = onlineRepo.getToplists(Platform.NETEASE)) {
            is Result.Success -> result.data
            else -> return emptyList()
        }
        if (toplists.isEmpty()) return emptyList()
        val toplist = toplists[refreshCount % toplists.size]
        return when (val detail = onlineRepo.getToplistDetail(Platform.NETEASE, toplist.id)) {
            is Result.Success -> {
                if (detail.data.size <= limit) {
                    detail.data
                } else {
                    val startIndex = (refreshCount * limit) % detail.data.size
                    buildList(limit) {
                        repeat(limit) { index ->
                            add(detail.data[(startIndex + index) % detail.data.size])
                        }
                    }
                }
            }
            else -> emptyList()
        }
    }

    private companion object {
        const val FAVORITE_SEED_LIMIT = 8
        const val RECENT_SEED_LIMIT = 18
        const val RECENT_HISTORY_LIMIT = 30
        const val MAX_PERSONALIZED_SEEDS = 3
        const val PERSONALIZED_SIMILAR_LIMIT = 8
        const val FAVORITE_SCORE_BASE = 8.0
        const val FAVORITE_SCORE_DECAY = 0.35
        const val FAVORITE_SCORE_FLOOR = 4.0
        const val RECENT_SCORE_BASE = 6.0
        const val RECENT_SCORE_DECAY = 0.25
        const val RECENT_SCORE_FLOOR = 1.0
        const val MAX_PLAYCOUNT_SCORE = 6
        const val RECOMMENDED_PLAYLIST_LIMIT = 6
        const val MID_TERM_LISTENING_BONUS = 1.0
        const val LONG_TERM_LISTENING_BONUS = 2.0
        const val MID_TERM_LISTENING_MS = 7L * 24 * 60 * 60 * 1000
        const val LONG_TERM_LISTENING_MS = 30L * 24 * 60 * 60 * 1000
        const val GUESS_SEED_ARTIST_COUNT = 3
        const val GUESS_HISTORY_LIMIT = 50
    }
}

private data class TasteSeed(
    val track: Track,
    val score: Double
)

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

private fun Track.uniqueKey(): String = "${platform.id}:$id"

private fun String.asRecommendationKeyword(): String {
    if (isBlank()) return ""
    return split("/", "、", "&", "，", ",", "|")
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: trim()
}
