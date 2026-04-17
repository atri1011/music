package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.dispatch.DispatchExecutor
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.online.OnlineMusicCommentCandidateResolver
import com.music.myapplication.data.repository.online.OnlineMusicCommentsFetcher
import com.music.myapplication.data.repository.online.OnlineMusicMediaResolver
import com.music.myapplication.data.repository.online.OnlineMusicNeteasePlaylistTrackResolver
import com.music.myapplication.data.repository.online.OnlineMusicQqAlbumInfoFetcher
import com.music.myapplication.data.repository.online.OnlineMusicSearchDelegate
import com.music.myapplication.data.repository.online.OnlineTrackCoverEnricher
import com.music.myapplication.domain.model.AlbumDetailResult
import com.music.myapplication.domain.model.AlbumInfo
import com.music.myapplication.domain.model.ArtistAlbum
import com.music.myapplication.domain.model.ArtistDetail
import com.music.myapplication.domain.model.ArtistRef
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaylistCategory
import com.music.myapplication.domain.model.PlaylistPreview
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchSuggestion
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LyricsResult
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.TrackCommentsResult
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicRepositoryImpl @Inject constructor(
    private val api: TuneHubApi,
    private val okHttpClient: OkHttpClient,
    private val dispatchExecutor: DispatchExecutor,
    private val json: Json,
    private val homeContentCacheStore: HomeContentCacheStore,
    private val preferences: PlayerPreferences? = null,
    private val neteaseCloudApiEnhancedApi: NeteaseCloudApiEnhancedApi? = null
) : OnlineMusicRepository {
    private val coverEnricher = OnlineTrackCoverEnricher(
        api = api,
        json = json,
        neteaseDetailBatchSize = NETEASE_DETAIL_BATCH_SIZE,
        qqDetailBatchSize = QQ_DETAIL_BATCH_SIZE,
        kuwoDetailBatchSize = KUWO_DETAIL_BATCH_SIZE
    )
    private val commentCandidateResolver = OnlineMusicCommentCandidateResolver(
        api = api,
        search = ::search
    )
    private val mediaResolver = OnlineMusicMediaResolver(
        api = api,
        okHttpClient = okHttpClient,
        resolveQqTrackCandidate = { track ->
            commentCandidateResolver.findQqTrackCandidate(track)
        }
    )
    private val searchDelegate = OnlineMusicSearchDelegate(
        api = api,
        getToplists = ::getToplists,
        getToplistDetailFast = ::getToplistDetailFast,
        officialSearchPageSizeLimit = OFFICIAL_SEARCH_PAGE_SIZE_LIMIT
    )
    private val commentsFetcher = OnlineMusicCommentsFetcher(
        api = api,
        json = json
    )
    private val neteasePlaylistTrackResolver = OnlineMusicNeteasePlaylistTrackResolver(
        api = api,
        neteaseDetailBatchSize = NETEASE_DETAIL_BATCH_SIZE
    )
    private val qqAlbumInfoFetcher = OnlineMusicQqAlbumInfoFetcher(
        api = api
    )

    override suspend fun search(
        platform: Platform, keyword: String, page: Int, pageSize: Int
    ): Result<List<Track>> {
        val result = dispatchExecutor.executeByMethod(
            platform = platform,
            function = "search",
            args = mapOf(
                "keyword" to keyword,
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
                "limit" to pageSize.toString(),
                "page_num" to page.toString(),
                "num_per_page" to pageSize.toString()
            )
        )
        return coverEnricher.enrich(platform, result)
    }

    override suspend fun getToplists(platform: Platform): Result<List<ToplistInfo>> {
        homeContentCacheStore.getCachedToplists(platform)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return Result.Success(it) }

        val result = fetchToplistsFromNetwork(platform)
        if (result is Result.Success && result.data.isNotEmpty()) {
            homeContentCacheStore.cacheToplists(platform, result.data)
        }
        return result
    }

    override suspend fun getToplistDetail(platform: Platform, id: String): Result<List<Track>> {
        val rawResult = getToplistDetailFast(platform, id)
        return when (rawResult) {
            is Result.Success -> Result.Success(enrichToplistTracks(platform, id, rawResult.data))
            is Result.Error -> rawResult
            is Result.Loading -> rawResult
        }
    }

    override suspend fun getToplistDetailFast(platform: Platform, id: String): Result<List<Track>> {
        homeContentCacheStore.getCachedToplistDetail(platform, id)
            ?.takeIf { isUsableToplistDetailCache(platform, it) }
            ?.let { return Result.Success(it) }

        val result = fetchToplistDetailFastFromNetwork(platform, id)
        if (result is Result.Success && isUsableToplistDetailCache(platform, result.data)) {
            homeContentCacheStore.cacheToplistDetail(platform, id, result.data)
        }
        return result
    }

    private fun isUsableToplistDetailCache(platform: Platform, tracks: List<Track>): Boolean {
        if (tracks.isEmpty()) return false
        return !(platform == Platform.NETEASE && tracks.size == 1)
    }

    private suspend fun fetchToplistsFromNetwork(platform: Platform): Result<List<ToplistInfo>> {
        if (platform == Platform.QQ) {
            val directToplists = runCatching { fetchQqToplistsDirect() }.getOrDefault(emptyList())
            if (directToplists.isNotEmpty()) {
                return Result.Success(directToplists)
            }
        }

        return try {
            val result = dispatchExecutor.executeByMethod(
                platform = platform,
                function = "toplists",
                args = emptyMap()
            )
            when (result) {
                is Result.Success -> Result.Success(
                    result.data.map { track ->
                        val coverUrl = if (platform == Platform.QQ) {
                            normalizeQqImageUrl(track.coverUrl)
                        } else {
                            track.coverUrl
                        }
                        ToplistInfo(
                            id = track.id,
                            name = track.title,
                            coverUrl = coverUrl
                        )
                    }
                )
                is Result.Error -> result
                is Result.Loading -> result
            }
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(cause = e))
        }
    }

    private suspend fun fetchToplistDetailFastFromNetwork(platform: Platform, id: String): Result<List<Track>> {
        if (platform == Platform.NETEASE) {
            return fetchNeteasePlaylistDetailDirect(id)
        }
        return dispatchExecutor.executeByMethod(
            platform = platform,
            function = "toplist",
            args = mapOf("id" to id)
        )
    }

    override suspend fun enrichToplistTracks(
        platform: Platform,
        id: String,
        tracks: List<Track>
    ): List<Track> {
        return coverEnricher.enrichToplistTracks(platform, id, tracks)
    }

    override suspend fun getPlaylistDetail(platform: Platform, id: String): Result<List<Track>> {
        if (platform == Platform.NETEASE) {
            return fetchNeteasePlaylistDetailDirect(id)
        }
        if (platform == Platform.QQ) {
            return fetchQqPlaylistDetailWithFallback(id)
        }
        val result = dispatchExecutor.executeByMethod(
            platform = platform,
            function = "playlist",
            args = mapOf("id" to id)
        )
        return coverEnricher.enrich(platform, result)
    }

    override suspend fun getAlbumDetail(platform: Platform, albumId: String): Result<List<Track>> {
        if (platform == Platform.NETEASE) {
            return fetchNeteaseAlbumDetailDirect(albumId)
        }
        if (platform == Platform.QQ) {
            return fetchQqAlbumDetailDirect(albumId)
        }
        val result = dispatchExecutor.executeByMethod(
            platform = platform,
            function = "albumDetail",
            args = mapOf("id" to albumId, "albumId" to albumId)
        )
        return coverEnricher.enrich(platform, result)
    }

    override suspend fun getAlbumInfo(platform: Platform, albumId: String): Result<AlbumInfo> {
        return when (platform) {
            Platform.NETEASE -> fetchNeteaseAlbumInfo(albumId)
            Platform.QQ -> fetchQqAlbumInfo(albumId)
            else -> Result.Success(AlbumInfo(id = albumId, platform = platform))
        }
    }

    override suspend fun getAlbumDetailFull(
        platform: Platform,
        albumId: String,
        albumNameHint: String,
        artistNameHint: String,
        coverUrlHint: String
    ): Result<AlbumDetailResult> {
        return when (platform) {
            Platform.NETEASE -> fetchNeteaseAlbumDetailFull(
                id = albumId,
                albumNameHint = albumNameHint,
                artistNameHint = artistNameHint,
                coverUrlHint = coverUrlHint
            )
            Platform.QQ -> fetchQqAlbumDetailFull(albumId)
            else -> {
                val tracksResult = getAlbumDetail(platform, albumId)
                when (tracksResult) {
                    is Result.Success -> Result.Success(
                        AlbumDetailResult(
                            info = AlbumInfo(id = albumId, platform = platform),
                            tracks = tracksResult.data
                        )
                    )
                    is Result.Error -> Result.Error(tracksResult.error)
                    is Result.Loading -> Result.Loading
                }
            }
        }
    }

    override suspend fun resolveShareUrl(url: String): String = mediaResolver.resolveShareUrl(url)

    override suspend fun resolvePlayableUrl(
        platform: Platform,
        songId: String,
        quality: String
    ): Result<String> = mediaResolver.resolvePlayableUrl(platform, songId, quality)

    override suspend fun resolveVideoUrl(track: Track): Result<String> =
        mediaResolver.resolveVideoUrl(track)

    override suspend fun getLyrics(platform: Platform, songId: String): Result<LyricsResult> =
        mediaResolver.getLyrics(platform, songId)

    override suspend fun getTrackComments(
        track: Track,
        page: Int,
        pageSize: Int
    ): Result<TrackCommentsResult> {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 50)
        var lastError: AppError? = null

        val neteaseSongId = commentCandidateResolver.resolveNeteaseCommentSongId(track)
        if (!neteaseSongId.isNullOrBlank()) {
            when (val result = commentsFetcher.fetchNeteaseTrackComments(neteaseSongId, safePage, safePageSize)) {
                is Result.Success -> {
                    if (result.data.totalCount > 0 || result.data.comments.isNotEmpty()) {
                        return result
                    }
                }

                is Result.Error -> lastError = result.error
                Result.Loading -> Unit
            }
        }

        val qqSongId = commentCandidateResolver.resolveQqCommentSongId(track)
        if (!qqSongId.isNullOrBlank()) {
            when (val result = commentsFetcher.fetchQqTrackComments(qqSongId, safePage, safePageSize)) {
                is Result.Success -> return result
                is Result.Error -> lastError = result.error
                Result.Loading -> Unit
            }
        }

        if (lastError != null) {
            return Result.Error(lastError)
        }

        return Result.Success(
            TrackCommentsResult(
                sourcePlatform = when {
                    !neteaseSongId.isNullOrBlank() -> Platform.NETEASE
                    !qqSongId.isNullOrBlank() -> Platform.QQ
                    else -> track.platform
                },
                totalCount = 0
            )
        )
    }

    private suspend fun fetchNeteasePlaylistDetailDirect(id: String): Result<List<Track>> {
        return try {
            val response = api.getNeteasePlaylistDetailV6(id = id)
            val code = extractApiCode(response) ?: -1
            if (code != 200) {
                return Result.Error(
                    AppError.Api(
                        message = extractApiMessage(response).ifBlank { "获取网易歌单详情失败" },
                        code = code
                    )
                )
            }

            val tracks = neteasePlaylistTrackResolver.resolvePlaylistTracks(response)
            if (tracks.isEmpty()) {
                return Result.Error(AppError.Parse(message = "解析网易歌单详情失败"))
            }

            Result.Success(coverEnricher.enrich(Platform.NETEASE, tracks))
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchQqPlaylistDetailWithFallback(id: String): Result<List<Track>> {
        val templateResult = dispatchExecutor.executeByMethod(
            platform = Platform.QQ,
            function = "playlist",
            args = mapOf("id" to id)
        )
        val templateTracks = (templateResult as? Result.Success)?.data.orEmpty()
        if (templateTracks.isNotEmpty()) {
            return coverEnricher.enrich(Platform.QQ, templateResult)
        }

        return when (val directResult = fetchQqPlaylistDetailDirect(id)) {
            is Result.Success -> {
                if (directResult.data.isNotEmpty()) {
                    coverEnricher.enrich(Platform.QQ, directResult)
                } else {
                    coverEnricher.enrich(Platform.QQ, templateResult)
                }
            }

            is Result.Error -> {
                if (templateResult is Result.Success) {
                    Result.Error(directResult.error)
                } else {
                    templateResult
                }
            }

            is Result.Loading -> coverEnricher.enrich(Platform.QQ, templateResult)
        }
    }

    private suspend fun fetchQqPlaylistDetailDirect(id: String): Result<List<Track>> {
        return try {
            val response = api.postQqMusicu(
                buildQqPlaylistDetailBody(
                    playlistId = id,
                    songNum = QQ_PLAYLIST_DETAIL_BATCH_SIZE
                )
            )
            val playlistResponse = (response as? JsonObject)?.getIgnoreCase("playlist")
            val playlistData = (playlistResponse as? JsonObject)?.getIgnoreCase("data")
            val code = extractApiCode(playlistResponse) ?: extractApiCode(playlistData)
            if (code != null && code != 0) {
                val message = extractApiMessage(playlistResponse)
                    .ifBlank { extractApiMessage(playlistData) }
                    .ifBlank { "获取 QQ 音乐歌单详情失败" }
                return Result.Error(
                    AppError.Api(
                        message = message,
                        code = code
                    )
                )
            }

            val tracks = extractQqPlaylistTracks(response)
            if (tracks.isEmpty()) {
                return Result.Error(AppError.Parse(message = "解析 QQ 音乐歌单详情失败"))
            }
            Result.Success(tracks)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchNeteaseAlbumDetailDirect(id: String): Result<List<Track>> {
        return try {
            val response = api.getNeteaseAlbumDetail(id = id)
            val code = extractApiCode(response)
            if (code != null && code != 200) {
                return Result.Error(
                    AppError.Api(
                        message = extractApiMessage(response).ifBlank { "获取网易专辑详情失败" },
                        code = code
                    )
                )
            }

            val tracks = extractNeteaseSongTracks(response).map { track ->
                if (track.albumId.isBlank()) track.copy(albumId = id) else track
            }
            if (tracks.isEmpty()) {
                return Result.Error(AppError.Parse(message = "解析网易专辑详情失败"))
            }

            Result.Success(coverEnricher.enrich(Platform.NETEASE, tracks))
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchQqAlbumDetailDirect(idOrMid: String): Result<List<Track>> {
        return try {
            val albumId = qqAlbumInfoFetcher.resolveAlbumId(idOrMid)
                ?: return Result.Error(AppError.Parse(message = "解析 QQ 音乐专辑信息失败"))
            val response = api.postQqMusicu(buildQqAlbumSongListBody(albumId))
            val songsResponse = (response as? JsonObject)?.get("songs")
            val code = extractApiCode(songsResponse)
            if (code != null && code != 0) {
                return Result.Error(
                    AppError.Api(
                        message = extractApiMessage(songsResponse).ifBlank { "获取 QQ 音乐专辑详情失败" },
                        code = code
                    )
                )
            }

            val tracks = extractQqAlbumTracks(response).map { track ->
                if (track.albumId.isBlank()) track.copy(albumId = albumId) else track
            }
            if (tracks.isEmpty()) {
                return Result.Error(AppError.Parse(message = "解析 QQ 音乐专辑详情失败"))
            }

            Result.Success(coverEnricher.enrich(Platform.QQ, tracks))
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchNeteaseAlbumInfo(id: String): Result<AlbumInfo> {
        return try {
            val response = api.getNeteaseAlbumDetail(id = id)
            val code = extractApiCode(response)
            if (code != null && code != 200) {
                return Result.Error(
                    AppError.Api(
                        message = extractApiMessage(response).ifBlank { "获取网易专辑信息失败" },
                        code = code
                    )
                )
            }
            val root = response as? JsonObject
            val album = root?.getIgnoreCase("album") as? JsonObject
                ?: return Result.Error(AppError.Parse(message = "解析网易专辑信息失败"))

            val legacyInfo = parseNeteaseAlbumInfo(id, album)
            val mergedInfo = mergeNeteaseAlbumMetadata(
                base = legacyInfo,
                enhanced = fetchNeteaseEnhancedAlbumInfo(id)
            )
            Result.Success(mergedInfo)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchQqAlbumInfo(idOrMid: String): Result<AlbumInfo> {
        return when (val albumData = qqAlbumInfoFetcher.fetchAlbumInfoData(idOrMid)) {
            is Result.Success -> Result.Success(parseQqAlbumInfo(idOrMid, albumData.data))
            is Result.Error -> Result.Error(albumData.error)
            is Result.Loading -> Result.Loading
        }
    }

    private suspend fun fetchNeteaseAlbumDetailFull(
        id: String,
        albumNameHint: String = "",
        artistNameHint: String = "",
        coverUrlHint: String = ""
    ): Result<AlbumDetailResult> {
        return try {
            val response = api.getNeteaseAlbumDetail(id = id)
            val code = extractApiCode(response)
            if (code != null && code != 200) {
                val message = extractApiMessage(response).ifBlank { "获取网易专辑详情失败" }
                if (shouldFallbackNeteaseAlbumDetail(code, message)) {
                    return fetchNeteaseAlbumDetailFallback(
                        albumId = id,
                        albumNameHint = albumNameHint,
                        artistNameHint = artistNameHint,
                        coverUrlHint = coverUrlHint
                    )
                }
                return Result.Error(
                    AppError.Api(
                        message = message,
                        code = code
                    )
                )
            }

            val tracks = extractNeteaseSongTracks(response).map { track ->
                if (track.albumId.isBlank()) track.copy(albumId = id) else track
            }
            val enrichedTracks = coverEnricher.enrich(Platform.NETEASE, tracks)

            val root = response as? JsonObject
            val album = root?.getIgnoreCase("album") as? JsonObject
            val baseInfo = if (album != null) parseNeteaseAlbumInfo(id, album) else AlbumInfo(
                id = id,
                platform = Platform.NETEASE
            )
            val info = mergeNeteaseAlbumMetadata(
                base = baseInfo,
                enhanced = fetchNeteaseEnhancedAlbumInfo(id)
            )

            if (enrichedTracks.isEmpty()) {
                val fallbackResult = fetchNeteaseAlbumDetailFallback(
                    albumId = id,
                    albumNameHint = info.name.ifBlank { albumNameHint },
                    artistNameHint = info.artistName.ifBlank { artistNameHint },
                    coverUrlHint = info.coverUrl.ifBlank { coverUrlHint }
                )
                if (fallbackResult is Result.Success && fallbackResult.data.tracks.isNotEmpty()) {
                    return fallbackResult
                }
            }

            Result.Success(AlbumDetailResult(info = info, tracks = enrichedTracks))
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchQqAlbumDetailFull(idOrMid: String): Result<AlbumDetailResult> {
        return try {
            val albumData = when (val result = qqAlbumInfoFetcher.fetchAlbumInfoData(idOrMid)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.error)
                is Result.Loading -> return Result.Loading
            }
            val albumId = extractQqAlbumIdFromInfoData(albumData)
                .orEmpty()
                .ifBlank { idOrMid.takeIf { it.isDigitsOnly() }.orEmpty() }
            if (albumId.isBlank()) {
                return Result.Error(AppError.Parse(message = "解析 QQ 音乐专辑信息失败"))
            }
            val info = parseQqAlbumInfo(idOrMid, albumData).copy(
                id = albumId
            )

            val response = api.postQqMusicu(buildQqAlbumSongListBody(albumId))
            val songsResponse = (response as? JsonObject)?.get("songs")
            val code = extractApiCode(songsResponse)
            if (code != null && code != 0) {
                return Result.Error(
                    AppError.Api(
                        message = extractApiMessage(songsResponse)
                            .ifBlank { "获取 QQ 音乐专辑详情失败" },
                        code = code
                    )
                )
            }

            val tracks = extractQqAlbumTracks(response).map { track ->
                if (track.albumId.isBlank()) track.copy(albumId = albumId) else track
            }
            val enrichedTracks = coverEnricher.enrich(Platform.QQ, tracks)

            Result.Success(AlbumDetailResult(info = info, tracks = enrichedTracks))
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private fun parseNeteaseAlbumInfo(id: String, album: JsonObject): AlbumInfo {
        val publishTimeMs = album.firstLongOf("publishTime") ?: 0L
        val publishYear = if (publishTimeMs > 0) {
            SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(publishTimeMs))
        } else ""

        val tags = extractTextList(album.getIgnoreCase("tags"))

        return AlbumInfo(
            id = id,
            name = album.firstStringOf("name").orEmpty(),
            artistName = extractJoinedNames(
                album.getIgnoreCase("artists") ?: album.getIgnoreCase("artist")
            ),
            coverUrl = album.firstStringOf("picUrl", "blurPicUrl").orEmpty(),
            publishTime = publishYear,
            description = album.firstStringOf("description").orEmpty()
                .ifBlank { album.firstStringOf("briefDesc").orEmpty() },
            company = album.firstStringOf("company").orEmpty(),
            subType = album.firstStringOf("subType", "type").orEmpty(),
            tags = tags,
            trackCount = (album.firstLongOf("size", "songCount") ?: 0L).toInt(),
            platform = Platform.NETEASE
        )
    }

    private suspend fun fetchNeteaseAlbumDetailFallback(
        albumId: String,
        albumNameHint: String,
        artistNameHint: String,
        coverUrlHint: String
    ): Result<AlbumDetailResult> {
        val keyword = buildNeteaseAlbumFallbackKeyword(albumNameHint, artistNameHint)
            ?: return Result.Error(
                AppError.Api(
                    message = "网易云专辑详情当前需要登录，且缺少完整的专辑和歌手检索信息",
                    code = -462
                )
            )

        val albumSearchResponse = api.searchNeteaseByType(
            keyword = keyword,
            type = SearchType.ALBUM.toNeteaseOfficialSearchType(),
            offset = 0,
            limit = OFFICIAL_SEARCH_PAGE_SIZE_LIMIT
        )
        val albumItems = extractNestedArray(
            ((albumSearchResponse as? JsonObject)?.getIgnoreCase("result") as? JsonObject),
            "albums",
            "album"
        ).orEmpty().mapNotNull { it as? JsonObject }

        val matchedAlbum = selectNeteaseAlbumFallbackCandidate(
            albumId = albumId,
            albumNameHint = albumNameHint,
            artistNameHint = artistNameHint,
            albums = albumItems
        )
        val resolvedAlbumId = matchedAlbum?.firstStringOf("id").orEmpty().ifBlank { albumId }
        val info = matchedAlbum?.let { parseNeteaseAlbumInfo(resolvedAlbumId, it) } ?: AlbumInfo(
            id = resolvedAlbumId,
            name = albumNameHint,
            artistName = artistNameHint,
            coverUrl = coverUrlHint,
            platform = Platform.NETEASE
        )

        val tracks = searchNeteaseAlbumTracksFallback(
            keyword = keyword,
            albumIds = setOf(albumId, resolvedAlbumId).filter(String::isNotBlank).toSet(),
            albumNameHint = info.name.ifBlank { albumNameHint },
            artistNameHint = info.artistName.ifBlank { artistNameHint },
            expectedTrackCount = info.trackCount
        )
        val enrichedTracks = coverEnricher.enrich(Platform.NETEASE, tracks)

        if (matchedAlbum == null && enrichedTracks.isEmpty()) {
            return Result.Error(
                AppError.Api(
                    message = "网易云专辑详情当前需要登录，且搜索回退未命中该专辑",
                    code = -462
                )
            )
        }

          return Result.Success(
              AlbumDetailResult(
                  info = mergeNeteaseAlbumMetadata(
                      base = info.copy(
                      id = info.id.ifBlank { albumId },
                      coverUrl = info.coverUrl.ifBlank { coverUrlHint }
                      ),
                      enhanced = fetchNeteaseEnhancedAlbumInfo(albumId)
                  ),
                  tracks = enrichedTracks
              )
          )
      }

    private suspend fun fetchNeteaseEnhancedAlbumInfo(albumId: String): AlbumInfo? {
        val configuredPreferences = preferences ?: return null
        val enhancedApi = neteaseCloudApiEnhancedApi ?: return null
        val baseUrl = configuredPreferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) return null

        return runCatching {
            enhancedApi.album(
                url = buildNeteaseEnhancedAlbumEndpoint(baseUrl),
                id = albumId
            )
        }.getOrNull()?.let { response ->
            val code = extractApiCode(response)
            if (code != null && code != 200) {
                null
            } else {
                val root = response as? JsonObject ?: return@let null
                val album = root.getIgnoreCase("album") as? JsonObject ?: return@let null
                parseNeteaseAlbumInfo(albumId, album)
            }
        }
    }

    private fun buildNeteaseEnhancedAlbumEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/album", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/album"
        }
    }

    private fun mergeNeteaseAlbumMetadata(base: AlbumInfo, enhanced: AlbumInfo?): AlbumInfo {
        if (enhanced == null) return base
        return base.copy(
            description = enhanced.description.ifBlank { base.description },
            tags = enhanced.tags.ifEmpty { base.tags }
        )
    }

    private suspend fun searchNeteaseAlbumTracksFallback(
        keyword: String,
        albumIds: Set<String>,
        albumNameHint: String,
        artistNameHint: String,
        expectedTrackCount: Int
    ): List<Track> {
        val matchedTracks = LinkedHashMap<String, Track>()

        for (page in 1..NETEASE_ALBUM_FALLBACK_MAX_PAGES) {
            val offset = (page - 1) * OFFICIAL_SEARCH_PAGE_SIZE_LIMIT
            val response = api.searchNeteaseByType(
                keyword = keyword,
                type = SearchType.SONG.toNeteaseOfficialSearchType(),
                offset = offset,
                limit = OFFICIAL_SEARCH_PAGE_SIZE_LIMIT
            )
            val result = (response as? JsonObject)?.getIgnoreCase("result") as? JsonObject ?: break
            val items = extractNestedArray(result, "songs", "song")
                .orEmpty()
                .mapNotNull { extractNeteaseTrack(it as? JsonObject) }
            if (items.isEmpty()) break

            items.filter { track ->
                track.albumId in albumIds ||
                    (track.album.isLikelySameTitle(albumNameHint) &&
                        track.artist.isLikelySameArtist(artistNameHint))
            }.forEach { track ->
                matchedTracks.putIfAbsent(track.id, track)
            }

            val totalCount = (result.firstLongOf("songCount") ?: 0L).toInt()
            val reachedExpectedCount = expectedTrackCount > 0 && matchedTracks.size >= expectedTrackCount
            val reachedLastPage = totalCount <= 0 || offset + items.size >= totalCount
            if (reachedExpectedCount || reachedLastPage) break
        }

        return matchedTracks.values.toList()
    }

    private fun selectNeteaseAlbumFallbackCandidate(
        albumId: String,
        albumNameHint: String,
        artistNameHint: String,
        albums: List<JsonObject>
    ): JsonObject? {
        if (albums.isEmpty()) return null

        return albums.firstOrNull { it.firstStringOf("id") == albumId }
            ?: albums.firstOrNull { album ->
                album.firstStringOf("name").orEmpty().isLikelySameTitle(albumNameHint) &&
                    extractJoinedNames(
                        album.getIgnoreCase("artists") ?: album.getIgnoreCase("artist")
                    ).isLikelySameArtist(artistNameHint)
            }
    }

    private fun buildNeteaseAlbumFallbackKeyword(
        albumNameHint: String,
        artistNameHint: String
    ): String? {
        val normalizedAlbumName = albumNameHint.trim()
        val normalizedArtistName = artistNameHint.trim()
        if (normalizedAlbumName.isBlank() || normalizedArtistName.isBlank()) return null

        val parts = listOf(normalizedAlbumName, normalizedArtistName)
            .filter(String::isNotBlank)
            .distinct()
        return parts.joinToString(" ")
    }

    private fun shouldFallbackNeteaseAlbumDetail(code: Int, message: String): Boolean {
        return code == -462 ||
            message.contains("登录", ignoreCase = true) ||
            message.contains("绑定手机")
    }

    private fun parseQqAlbumInfo(idOrMid: String, albumData: JsonObject): AlbumInfo {
        val basicInfo = albumData.getIgnoreCase("basicInfo") as? JsonObject
            ?: return AlbumInfo(id = idOrMid, platform = Platform.QQ)
        val companyInfo = albumData.getIgnoreCase("company") as? JsonObject
        val singerList = (albumData.getIgnoreCase("singer") as? JsonObject)
            ?.getIgnoreCase("singerList")

        val company = companyInfo?.firstStringOf("name").orEmpty()
            .ifBlank {
                (basicInfo.getIgnoreCase("company") as? JsonObject)
                    ?.firstStringOf("name")
                    .orEmpty()
            }
            .ifBlank { basicInfo.firstStringOf("company").orEmpty() }

        val genre = (basicInfo.getIgnoreCase("genre") as? JsonObject)
            ?.firstStringOf("name")
            .orEmpty()
            .ifBlank { basicInfo.firstStringOf("genre", "genreNew").orEmpty() }

        val language = (basicInfo.getIgnoreCase("language") as? JsonObject)
            ?.firstStringOf("name")
            .orEmpty()
            .ifBlank { basicInfo.firstStringOf("language").orEmpty() }

        val tags = extractTextList(basicInfo.getIgnoreCase("recLabels"))
            .ifEmpty { extractTextList(basicInfo.getIgnoreCase("tags")) }
            .ifEmpty { extractTextList(basicInfo.getIgnoreCase("tagList")) }
            .ifEmpty { extractTextList(basicInfo.getIgnoreCase("genres")) }
            .ifEmpty {
                extractTextList(
                    basicInfo.getIgnoreCase("awards"),
                    "detailAward",
                    "name"
                )
            }

        val albumMid = basicInfo.firstStringOf("albumMid", "albumMID", "mid").orEmpty()
        val publishYear = basicInfo.firstStringOf("publishDate", "public_time").orEmpty().take(4)

        return AlbumInfo(
            id = basicInfo.firstStringOf("albumID", "albumId", "id").orEmpty()
                .ifBlank { idOrMid },
            name = basicInfo.firstStringOf("albumName", "name", "title").orEmpty(),
            artistName = basicInfo.firstStringOf("singerName", "singer_name").orEmpty()
                .ifBlank { extractJoinedNames(singerList) },
            coverUrl = buildQqAlbumCoverUrl(albumMid),
            publishTime = publishYear,
            description = basicInfo.firstStringOf("desc", "description", "introduction")
                .orEmpty()
                .ifBlank { basicInfo.firstStringOf("recText").orEmpty() }
                .ifBlank { companyInfo?.firstStringOf("brief").orEmpty() },
            company = company,
            genre = genre,
            language = language,
            subType = basicInfo.firstStringOf("albumType", "subType", "type").orEmpty(),
            tags = tags,
            trackCount = (basicInfo.firstLongOf("totalNum", "track_num") ?: 0L).toInt(),
            platform = Platform.QQ
        )
    }

    private suspend fun fetchQqToplistsDirect(): List<ToplistInfo> {
        val response = api.postQqMusicu(body = buildQqToplistsRequestBody())
        return extractQqToplists(response)
    }

    // ── Hot Search ──

    override suspend fun getHotSearchKeywords(platform: Platform): Result<List<String>> =
        searchDelegate.getHotSearchKeywords(platform)

    override suspend fun getSearchSuggestions(
        platform: Platform,
        keyword: String
    ): Result<List<SearchSuggestion>> = searchDelegate.getSearchSuggestions(platform, keyword)

    // ── Artist ──

    override suspend fun resolveArtistRef(track: Track): Result<ArtistRef> =
        withContext(Dispatchers.IO) {
            try {
                when (track.platform) {
                    Platform.NETEASE -> {
                        val resp = api.getNeteaseSongDetail(ids = "[${track.id}]")
                        val songs = (resp as? JsonObject)?.get("songs") as? JsonArray
                        val song = songs?.firstOrNull() as? JsonObject
                        val artists = (song?.getIgnoreCase("ar") ?: song?.getIgnoreCase("artists")) as? JsonArray
                        val artist = artists?.firstOrNull() as? JsonObject
                        val artistId = artist?.firstStringOf("id")
                        if (artistId.isNullOrBlank()) {
                            Result.Error(AppError.Api(message = "artist not found"))
                        } else {
                            Result.Success(ArtistRef(artistId, track.artist, track.platform))
                        }
                    }
                    Platform.QQ -> {
                        val resp = api.getQqSongDetail(songMid = track.id)
                        val data = (resp as? JsonObject)?.get("data") as? JsonArray
                        val song = data?.firstOrNull() as? JsonObject
                        val singers = song?.get("singer") as? JsonArray
                        val singer = singers?.firstOrNull() as? JsonObject
                        val singerMid = singer?.firstStringOf("mid")
                        if (singerMid.isNullOrBlank()) {
                            Result.Error(AppError.Api(message = "artist not found"))
                        } else {
                            Result.Success(ArtistRef(singerMid, track.artist, track.platform))
                        }
                    }
                    else -> Result.Error(AppError.Api(message = "unsupported platform"))
                }
            } catch (e: Exception) {
                Result.Error(AppError.Network(cause = e))
            }
        }

    override suspend fun getArtistDetail(
        artistId: String, platform: Platform
    ): Result<ArtistDetail> = withContext(Dispatchers.IO) {
        try {
            when (platform) {
                Platform.NETEASE -> {
                    val detailResp = api.getNeteaseArtistDetail(artistId)
                    val root = detailResp as? JsonObject ?: return@withContext Result.Error(AppError.Api(message = "empty"))
                    val artistObj = root["artist"] as? JsonObject
                    val name = artistObj?.firstStringOf("name").orEmpty()
                    val avatarUrl = artistObj?.firstStringOf("picUrl", "img1v1Url").orEmpty()
                    val hotSongs = (root["hotSongs"] as? JsonArray)?.mapNotNull {
                        extractNeteaseTrack(it as? JsonObject)
                    } ?: emptyList()

                    val albumResp = runCatching { api.getNeteaseArtistAlbums(artistId) }.getOrNull()
                    val albums = extractNeteaseArtistAlbums(albumResp)

                    if (name.isBlank() && avatarUrl.isBlank() && hotSongs.isEmpty() && albums.isEmpty()) {
                        return@withContext Result.Error(AppError.Api(message = "artist not found"))
                    }

                    val enhancedMetadata = fetchNeteaseEnhancedArtistMetadata(
                        artistId = artistId,
                        preferences = preferences,
                        enhancedApi = neteaseCloudApiEnhancedApi
                    )
                    Result.Success(
                        ArtistDetail(
                            id = artistId,
                            name = name,
                            platform = platform,
                            avatarUrl = avatarUrl,
                            description = enhancedMetadata?.description.orEmpty(),
                            tags = enhancedMetadata?.tags.orEmpty(),
                            hotSongs = hotSongs,
                            albums = albums
                        )
                    )
                }
                Platform.QQ -> {
                    val body = buildQqArtistDetailBody(artistId)
                    val resp = api.postQqMusicu(body)
                    val root = resp as? JsonObject ?: return@withContext Result.Error(AppError.Api(message = "empty"))
                    val songsData = (root["songs"] as? JsonObject)?.get("data") as? JsonObject
                    val albumsData = (root["albums"] as? JsonObject)?.get("data") as? JsonObject
                    val albumListRaw = albumsData?.get("albumList") as? JsonArray
                    val songList = (songsData?.get("songList") as? JsonArray)?.mapNotNull { el ->
                        extractQqArtistSong(el as? JsonObject)
                    } ?: emptyList()
                    val albums = albumListRaw?.mapNotNull { el ->
                        extractQqArtistAlbum(el as? JsonObject)
                    } ?: emptyList()
                    val firstSongArtist = songList.firstOrNull()?.artist.orEmpty()
                    val firstAlbumArtist = (albumListRaw?.firstOrNull() as? JsonObject)
                        ?.firstStringOf("singerName")
                        .orEmpty()
                    val name = firstSongArtist.ifBlank { firstAlbumArtist }.ifBlank { artistId }
                    val avatarUrl = buildQqSingerCoverUrl(artistId)

                    if (songList.isEmpty() && albums.isEmpty()) {
                        return@withContext Result.Error(AppError.Api(message = "artist not found"))
                    }

                    Result.Success(
                        ArtistDetail(
                            artistId,
                            name = name,
                            platform = platform,
                            avatarUrl = avatarUrl,
                            hotSongs = songList,
                            albums = albums
                        )
                    )
                }
                else -> Result.Error(AppError.Api(message = "unsupported platform"))
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    // ── Playlist Browse ──

    override suspend fun getPlaylistCategories(platform: Platform): Result<List<PlaylistCategory>> =
        withContext(Dispatchers.IO) {
            try {
                when (platform) {
                    Platform.NETEASE -> {
                        val resp = api.getNeteasePlaylistCategories()
                        val root = resp as? JsonObject ?: return@withContext Result.Success(emptyList())
                        val categories = mutableListOf(PlaylistCategory("全部", true))
                        val subs = root["sub"] as? JsonArray ?: return@withContext Result.Success(categories)
                        subs.forEach { el ->
                            val cat = el as? JsonObject ?: return@forEach
                            val name = cat.firstStringOf("name") ?: return@forEach
                            val hot = (cat.getIgnoreCase("hot") as? JsonPrimitive)?.contentOrNull == "true"
                            categories += PlaylistCategory(name, hot)
                        }
                        Result.Success(categories)
                    }
                    Platform.QQ -> {
                        Result.Success(
                            qqPlaylistCategoryIdMap.keys.mapIndexed { index, name ->
                                PlaylistCategory(name, hot = index == 0)
                            }
                        )
                    }
                    else -> Result.Success(emptyList())
                }
            } catch (e: Exception) {
                Result.Error(AppError.Network(cause = e))
            }
        }

    override suspend fun getPlaylistsByCategory(
        platform: Platform, category: String, page: Int, pageSize: Int
    ): Result<List<PlaylistPreview>> = withContext(Dispatchers.IO) {
        try {
            when (platform) {
                Platform.NETEASE -> {
                    val offset = (page - 1) * pageSize
                    val resp = api.getNeteasePlaylistByCategory(
                        category = category,
                        offset = offset,
                        limit = pageSize
                    )
                    val root = resp as? JsonObject ?: return@withContext Result.Success(emptyList())
                    val playlists = root["playlists"] as? JsonArray ?: return@withContext Result.Success(emptyList())
                    val result = playlists.mapNotNull { el ->
                        val item = el as? JsonObject ?: return@mapNotNull null
                        val id = item.firstStringOf("id") ?: return@mapNotNull null
                        PlaylistPreview(
                            id = id,
                            name = item.firstStringOf("name").orEmpty(),
                            coverUrl = item.firstStringOf("coverImgUrl", "picUrl").orEmpty(),
                            playCount = item.firstLongOf("playCount", "playcount") ?: 0L,
                            description = item.firstStringOf("description").orEmpty(),
                            platform = Platform.NETEASE
                        )
                    }
                    Result.Success(result)
                }
                Platform.QQ -> {
                    val body = buildQqPlaylistByCategoryBody(category, page, pageSize)
                    val resp = api.postQqMusicu(body)
                    val root = resp as? JsonObject ?: return@withContext Result.Success(emptyList())
                    val data = (root["playlist"] as? JsonObject)?.get("data") as? JsonObject
                    val list = data?.get("v_playlist") as? JsonArray ?: return@withContext Result.Success(emptyList())
                    val result = list.mapNotNull { el ->
                        val item = el as? JsonObject ?: return@mapNotNull null
                        val id = item.firstStringOf("tid", "dissid") ?: return@mapNotNull null
                        PlaylistPreview(
                            id = id,
                            name = item.firstStringOf("title", "diss_name").orEmpty(),
                            coverUrl = normalizeQqImageUrl(item.firstStringOf("cover_url_big", "imgurl", "logo").orEmpty()),
                            playCount = item.firstLongOf("listen_num", "access_num") ?: 0L,
                            description = item.firstStringOf("desc", "introduction").orEmpty(),
                            platform = Platform.QQ
                        )
                    }
                    Result.Success(result)
                }
                else -> Result.Success(emptyList())
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    override suspend fun searchArtists(
        platform: Platform, keyword: String, page: Int, pageSize: Int
    ): Result<List<SearchResultItem>> {
        return searchDelegate.searchByType(platform, keyword, page, pageSize, SearchType.ARTIST)
    }

    override suspend fun searchAlbums(
        platform: Platform, keyword: String, page: Int, pageSize: Int
    ): Result<List<SearchResultItem>> {
        return searchDelegate.searchByType(platform, keyword, page, pageSize, SearchType.ALBUM)
    }

    override suspend fun searchPlaylists(
        platform: Platform, keyword: String, page: Int, pageSize: Int
    ): Result<List<SearchResultItem>> {
        return searchDelegate.searchByType(platform, keyword, page, pageSize, SearchType.PLAYLIST)
    }

    private companion object {
        const val NETEASE_DETAIL_BATCH_SIZE = 50
        const val NETEASE_ALBUM_FALLBACK_MAX_PAGES = 4
        const val QQ_DETAIL_BATCH_SIZE = 20
        const val QQ_PLAYLIST_DETAIL_BATCH_SIZE = 500
        const val KUWO_DETAIL_BATCH_SIZE = 50
        const val OFFICIAL_SEARCH_PAGE_SIZE_LIMIT = 50
    }
}
