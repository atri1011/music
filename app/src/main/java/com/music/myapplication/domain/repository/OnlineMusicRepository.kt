package com.music.myapplication.domain.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.ArtistDetail
import com.music.myapplication.domain.model.ArtistRef
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaylistCategory
import com.music.myapplication.domain.model.PlaylistPreview
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchSuggestion
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.Serializable

data class LyricsResult(val lyric: String, val translation: String? = null)

data class TrackComment(
    val id: String,
    val authorName: String,
    val content: String,
    val likedCount: Int = 0,
    val timeMs: Long = 0L,
    val avatarUrl: String = ""
)

enum class TrackCommentSort {
    HOT,
    LATEST,
    RECOMMENDED
}

data class TrackCommentsResult(
    val sourcePlatform: Platform,
    val totalCount: Int,
    val hotComments: List<TrackComment> = emptyList(),
    val latestComments: List<TrackComment> = emptyList(),
    val recommendedComments: List<TrackComment> = emptyList()
) {
    val comments: List<TrackComment>
        get() = when {
            hotComments.isNotEmpty() -> hotComments
            latestComments.isNotEmpty() -> latestComments
            recommendedComments.isNotEmpty() -> recommendedComments
            else -> emptyList()
        }

    fun commentsOf(sort: TrackCommentSort): List<TrackComment> = when (sort) {
        TrackCommentSort.HOT -> hotComments
        TrackCommentSort.LATEST -> latestComments
        TrackCommentSort.RECOMMENDED -> recommendedComments
    }
}

interface OnlineMusicRepository {
    suspend fun search(platform: Platform, keyword: String, page: Int = 1, pageSize: Int = 20): Result<List<Track>>
    suspend fun getToplists(platform: Platform): Result<List<ToplistInfo>>
    suspend fun getToplistDetailFast(platform: Platform, id: String): Result<List<Track>>
    suspend fun enrichToplistTracks(platform: Platform, id: String, tracks: List<Track>): List<Track>
    suspend fun getToplistDetail(platform: Platform, id: String): Result<List<Track>>
    suspend fun getPlaylistDetail(platform: Platform, id: String): Result<List<Track>>
    suspend fun getAlbumDetail(platform: Platform, albumId: String): Result<List<Track>>
    suspend fun getTrackComments(track: Track, page: Int = 1, pageSize: Int = 20): Result<TrackCommentsResult>
    suspend fun resolveShareUrl(url: String): String
    suspend fun resolvePlayableUrl(platform: Platform, songId: String, quality: String = "128k"): Result<String>
    suspend fun resolveVideoUrl(track: Track): Result<String>
    suspend fun getLyrics(platform: Platform, songId: String): Result<LyricsResult>

    suspend fun getHotSearchKeywords(platform: Platform): Result<List<String>>
    suspend fun getSearchSuggestions(platform: Platform, keyword: String): Result<List<SearchSuggestion>>
    suspend fun resolveArtistRef(track: Track): Result<ArtistRef>
    suspend fun getArtistDetail(artistId: String, platform: Platform): Result<ArtistDetail>
    suspend fun getPlaylistCategories(platform: Platform): Result<List<PlaylistCategory>>
    suspend fun getPlaylistsByCategory(platform: Platform, category: String, page: Int = 1, pageSize: Int = 30): Result<List<PlaylistPreview>>

    suspend fun searchArtists(platform: Platform, keyword: String, page: Int = 1, pageSize: Int = 20): Result<List<SearchResultItem>>
    suspend fun searchAlbums(platform: Platform, keyword: String, page: Int = 1, pageSize: Int = 20): Result<List<SearchResultItem>>
    suspend fun searchPlaylists(platform: Platform, keyword: String, page: Int = 1, pageSize: Int = 20): Result<List<SearchResultItem>>
}

@Serializable
data class ToplistInfo(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = ""
)
