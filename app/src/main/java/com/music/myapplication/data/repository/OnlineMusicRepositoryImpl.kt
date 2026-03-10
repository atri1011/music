package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.dispatch.DispatchExecutor
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.online.OnlineMusicMediaResolver
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
import com.music.myapplication.domain.repository.TrackComment
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
import kotlin.math.abs

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
    private val mediaResolver = OnlineMusicMediaResolver(
        api = api,
        okHttpClient = okHttpClient,
        resolveQqTrackCandidate = { track ->
            findCommentTrackCandidate(track, Platform.QQ)
        }
    )
    private val searchDelegate = OnlineMusicSearchDelegate(
        api = api,
        getToplists = ::getToplists,
        getToplistDetailFast = ::getToplistDetailFast,
        officialSearchPageSizeLimit = OFFICIAL_SEARCH_PAGE_SIZE_LIMIT
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

        val neteaseSongId = resolveNeteaseCommentSongId(track)
        if (!neteaseSongId.isNullOrBlank()) {
            when (val result = fetchNeteaseTrackComments(neteaseSongId, safePage, safePageSize)) {
                is Result.Success -> {
                    if (result.data.totalCount > 0 || result.data.comments.isNotEmpty()) {
                        return result
                    }
                }

                is Result.Error -> lastError = result.error
                Result.Loading -> Unit
            }
        }

        val qqSongId = resolveQqCommentSongId(track)
        if (!qqSongId.isNullOrBlank()) {
            when (val result = fetchQqTrackComments(qqSongId, safePage, safePageSize)) {
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

            val tracks = resolveNeteasePlaylistTracks(response)
            if (tracks.isEmpty()) {
                return Result.Error(AppError.Parse(message = "解析网易歌单详情失败"))
            }

            Result.Success(coverEnricher.enrich(Platform.NETEASE, tracks))
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
            val albumId = resolveQqAlbumId(idOrMid)
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
        return when (val albumData = fetchQqAlbumInfoData(idOrMid)) {
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
            val albumData = when (val result = fetchQqAlbumInfoData(idOrMid)) {
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

    private suspend fun fetchQqAlbumInfoData(idOrMid: String): Result<JsonObject> {
        return try {
            val response = api.postQqMusicu(buildQqAlbumInfoBody(idOrMid))
            val albumResponse = (response as? JsonObject)?.get("album")
            val code = extractApiCode(albumResponse)
            if (code != null && code != 0) {
                return Result.Error(
                    AppError.Api(
                        message = extractApiMessage(albumResponse).ifBlank { "获取 QQ 音乐专辑信息失败" },
                        code = code
                    )
                )
            }

            val albumData = extractQqAlbumInfoData(response)
                ?: return Result.Error(AppError.Parse(message = "解析 QQ 音乐专辑信息失败"))
            Result.Success(albumData)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun resolveQqAlbumId(idOrMid: String): String? {
        if (idOrMid.isBlank()) return null
        if (idOrMid.isDigitsOnly()) return idOrMid

        return runCatching {
            api.postQqMusicu(buildQqAlbumInfoBody(idOrMid))
        }.getOrNull()?.let(::extractQqAlbumId)
    }

    private suspend fun resolveNeteasePlaylistTracks(response: JsonElement): List<Track> {
        val playlistTracks = extractNeteasePlaylistTracks(response)
        val trackIds = extractNeteasePlaylistTrackIds(response)
        if (trackIds.isEmpty()) return playlistTracks

        val resolvedTracks = LinkedHashMap<String, Track>()
        playlistTracks.forEach { track ->
            if (track.id.isNotBlank()) {
                resolvedTracks.putIfAbsent(track.id, track)
            }
        }

        val missingTrackIds = trackIds.filterNot(resolvedTracks::containsKey)
        if (missingTrackIds.isNotEmpty()) {
            fetchNeteaseSongs(missingTrackIds).forEach { track ->
                if (track.id.isNotBlank()) {
                    resolvedTracks.putIfAbsent(track.id, track)
                }
            }
        }

        return trackIds.mapNotNull(resolvedTracks::get)
            .ifEmpty { playlistTracks }
    }

    private suspend fun fetchNeteaseSongs(songIds: List<String>): List<Track> {
        if (songIds.isEmpty()) return emptyList()

        val tracks = mutableListOf<Track>()
        songIds.chunked(NETEASE_DETAIL_BATCH_SIZE).forEach { chunk ->
            val idsParam = chunk.joinToString(prefix = "[", postfix = "]")
            val response = api.getNeteaseSongDetail(ids = idsParam)
            tracks += extractNeteaseSongTracks(response)
        }
        return tracks
    }

    private suspend fun fetchQqToplistsDirect(): List<ToplistInfo> {
        val response = api.postQqMusicu(body = buildQqToplistsRequestBody())
        return extractQqToplists(response)
    }

    private suspend fun resolveNeteaseCommentSongId(track: Track): String? {
        if (track.platform == Platform.NETEASE && track.id.isDigitsOnly()) return track.id
        return findCommentTrackCandidate(track, Platform.NETEASE)
            ?.id
            ?.takeIf { it.isDigitsOnly() }
    }

    private suspend fun resolveQqCommentSongId(track: Track): String? {
        if (track.platform == Platform.QQ) {
            resolveQqSongId(track.id)?.let { return it }
        }

        val candidate = findCommentTrackCandidate(track, Platform.QQ) ?: return null
        return resolveQqSongId(candidate.id)
    }

    private suspend fun findCommentTrackCandidate(track: Track, platform: Platform): Track? {
        val keyword = listOf(track.title, track.artist)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val searchResult = search(
            platform = platform,
            keyword = keyword,
            page = 1,
            pageSize = 20
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

    private suspend fun fetchNeteaseTrackComments(
        songId: String,
        page: Int,
        pageSize: Int
    ): Result<TrackCommentsResult> {
        return try {
            val legacyResponse = api.getNeteaseSongComments(
                songId = songId,
                limit = pageSize,
                offset = (page - 1) * pageSize
            )
            val code = extractApiCode(legacyResponse) ?: -1
            if (code != 200) {
                Result.Error(
                    AppError.Api(
                        message = extractApiMessage(legacyResponse).ifBlank { "获取网易云评论失败" },
                        code = code
                    )
                )
            } else {
                val legacyComments = extractNeteaseTrackComments(legacyResponse)
                val latestPage = fetchNeteaseSortedCommentPage(
                    songId = songId,
                    page = page,
                    pageSize = pageSize,
                    sortType = NETEASE_COMMENT_SORT_LATEST
                )
                val recommendedPage = fetchNeteaseSortedCommentPage(
                    songId = songId,
                    page = page,
                    pageSize = pageSize,
                    sortType = NETEASE_COMMENT_SORT_RECOMMENDED
                )
                val totalCount = maxOf(
                    legacyComments.totalCount,
                    latestPage?.totalCount ?: 0,
                    recommendedPage?.totalCount ?: 0
                )
                Result.Success(
                    TrackCommentsResult(
                        sourcePlatform = Platform.NETEASE,
                        totalCount = totalCount,
                        hotComments = legacyComments.hotComments,
                        latestComments = latestPage?.comments.takeUnless { it.isNullOrEmpty() }
                            ?: legacyComments.latestComments,
                        recommendedComments = recommendedPage?.comments.takeUnless { it.isNullOrEmpty() }
                            ?: legacyComments.recommendedComments
                    )
                )
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchQqTrackComments(
        songId: String,
        page: Int,
        pageSize: Int
    ): Result<TrackCommentsResult> {
        return try {
            val rawResponse = api.getQqSongCommentsRaw(
                songId = songId,
                pageNum = (page - 1).coerceAtLeast(0),
                pageSize = pageSize
            ).use { it.string() }

            val extracted = extractQqTrackComments(rawResponse, json)
                ?: return Result.Error(AppError.Parse(message = "解析 QQ 音乐评论失败"))

            Result.Success(
                TrackCommentsResult(
                    sourcePlatform = Platform.QQ,
                    totalCount = extracted.totalCount,
                    latestComments = extracted.comments
                )
            )
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun fetchNeteaseSortedCommentPage(
        songId: String,
        page: Int,
        pageSize: Int,
        sortType: Int
    ): ExtractedCommentPage? {
        val response = runCatching {
            api.getNeteaseSortedSongComments(
                songId = songId,
                pageNo = page,
                pageSize = pageSize,
                sortType = sortType
            )
        }.getOrNull() ?: return null

        if ((extractApiCode(response) ?: -1) != 200) return null
        return extractNeteaseSortedTrackComments(response)
    }

    private fun String.isDigitsOnly(): Boolean = isNotEmpty() && all(Char::isDigit)

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
        const val NETEASE_COMMENT_SORT_RECOMMENDED = 1
        const val NETEASE_COMMENT_SORT_LATEST = 3
        const val QQ_DETAIL_BATCH_SIZE = 20
        const val KUWO_DETAIL_BATCH_SIZE = 50
        const val OFFICIAL_SEARCH_PAGE_SIZE_LIMIT = 50
    }
}

private fun buildQqToplistsRequestBody(): JsonElement = buildJsonObject {
    put(
        "comm",
        buildJsonObject {
            put("cv", 4747474)
            put("ct", 24)
            put("format", "json")
            put("inCharset", "utf-8")
            put("outCharset", "utf-8")
            put("uin", 0)
        }
    )
    put(
        "toplist",
        buildJsonObject {
            put("module", "musicToplist.ToplistInfoServer")
            put("method", "GetAll")
            put("param", buildJsonObject { })
        }
    )
}

internal fun buildQqToplistDetailRequestBody(topId: Int): JsonElement = buildJsonObject {
    put(
        "comm",
        buildJsonObject {
            put("cv", 4747474)
            put("ct", 24)
            put("format", "json")
            put("inCharset", "utf-8")
            put("outCharset", "utf-8")
            put("uin", 0)
        }
    )
    put(
        "toplist",
        buildJsonObject {
            put("module", "musicToplist.ToplistInfoServer")
            put("method", "GetDetail")
            put(
                "param",
                buildJsonObject {
                    put("topid", topId)
                    put("num", 300)
                    put("period", "")
                }
            )
        }
    )
}

internal fun extractQqToplists(data: JsonElement?): List<ToplistInfo> {
    val root = data as? JsonObject ?: return emptyList()
    val groups = (((root.getIgnoreCase("toplist") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject)
        ?.getIgnoreCase("group") as? JsonArray) ?: return emptyList()

    val toplists = mutableListOf<ToplistInfo>()
    groups.forEach { groupElement ->
        val group = groupElement as? JsonObject ?: return@forEach
        val items = group.getIgnoreCase("toplist") as? JsonArray ?: return@forEach
        items.forEach { itemElement ->
            val item = itemElement as? JsonObject ?: return@forEach
            val id = item.firstStringOf("topId", "topid", "id").orEmpty()
            val name = item.firstStringOf("title", "name").orEmpty()
            if (id.isBlank() || name.isBlank()) return@forEach
            val coverUrl = normalizeQqImageUrl(
                item.firstStringOf("headPicUrl", "frontPicUrl", "picUrl", "pic").orEmpty()
            )
            toplists += ToplistInfo(
                id = id,
                name = name,
                coverUrl = coverUrl
            )
        }
    }

    return toplists
}

internal fun extractNeteasePlaylistTracks(data: JsonElement?): List<Track> {
    val root = data as? JsonObject ?: return emptyList()
    val playlist = (root.getIgnoreCase("playlist") ?: root.getIgnoreCase("result")) as? JsonObject
        ?: return emptyList()
    val tracks = playlist.getIgnoreCase("tracks") as? JsonArray ?: return emptyList()

    return tracks.mapNotNull { itemElement ->
        extractNeteaseTrack(itemElement as? JsonObject)
    }
}

internal fun extractNeteasePlaylistTrackIds(data: JsonElement?): List<String> {
    val root = data as? JsonObject ?: return emptyList()
    val playlist = (root.getIgnoreCase("playlist") ?: root.getIgnoreCase("result")) as? JsonObject
        ?: return emptyList()
    val trackIds = playlist.getIgnoreCase("trackIds") as? JsonArray ?: return emptyList()

    return trackIds.mapNotNull { itemElement ->
        (itemElement as? JsonObject)
            ?.firstStringOf("id")
            ?.takeIf { it.isNotBlank() }
    }.distinct()
}

internal fun extractNeteaseSongTracks(data: JsonElement?): List<Track> {
    val songs = ((data as? JsonObject)?.getIgnoreCase("songs") as? JsonArray) ?: return emptyList()
    return songs.mapNotNull { itemElement ->
        extractNeteaseTrack(itemElement as? JsonObject)
    }
}

internal fun extractQqToplistSongCoverMap(data: JsonElement?): Map<String, String> {
    val root = data as? JsonObject ?: return emptyMap()
    val songs = (((root.getIgnoreCase("toplist") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject)
        ?.getIgnoreCase("songInfoList") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songMid = song.firstStringOf("mid", "songmid", "songMid").orEmpty()
        val songId = song.firstStringOf("id", "songId", "songid").orEmpty()
        if (songMid.isBlank() && songId.isBlank()) return@forEach

        val album = song.getIgnoreCase("album") as? JsonObject
        val albumMid = album?.firstStringOf("mid") ?: song.firstStringOf("albumMid", "albummid")
        val coverUrl = buildQqAlbumCoverUrl(albumMid.orEmpty())
        if (coverUrl.isNotBlank()) {
            if (songMid.isNotBlank()) coverMap[songMid] = coverUrl
            if (songId.isNotBlank()) coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun extractNeteaseSongCoverMap(data: JsonElement?): Map<String, String> {
    val songs = ((data as? JsonObject)?.getIgnoreCase("songs") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songId = song.firstStringOf("id").orEmpty()
        if (songId.isBlank()) return@forEach

        val album = (song.getIgnoreCase("album") ?: song.getIgnoreCase("al")) as? JsonObject
        val coverUrl = album?.firstStringOf("picUrl", "blurPicUrl").orEmpty()
        if (coverUrl.isNotBlank()) {
            coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun extractQqSongCoverMap(data: JsonElement?): Map<String, String> {
    val songs = ((data as? JsonObject)?.getIgnoreCase("data") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songMid = song.firstStringOf("mid", "songmid", "songMid").orEmpty()
        val songId = song.firstStringOf("id", "songId", "songid").orEmpty()
        if (songMid.isBlank() && songId.isBlank()) return@forEach

        val album = song.getIgnoreCase("album") as? JsonObject
        val albumMid = album?.firstStringOf("mid") ?: song.firstStringOf("albumMid", "albummid")
        val coverUrl = buildQqAlbumCoverUrl(albumMid.orEmpty())
        if (coverUrl.isNotBlank()) {
            if (songMid.isNotBlank()) coverMap[songMid] = coverUrl
            if (songId.isNotBlank()) coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun extractKuwoSongCoverMap(rawResponse: String, json: Json): Map<String, String> {
    if (rawResponse.isBlank()) return emptyMap()

    val normalized = rawResponse.replace('\'', '"')
    val root = runCatching { json.parseToJsonElement(normalized) }.getOrNull() as? JsonObject ?: return emptyMap()
    val songs = (root.getIgnoreCase("abslist") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songId = song.firstStringOf("id")
            ?.takeIf { it.isNotBlank() }
            ?: song.firstStringOf("MUSICRID", "musicrid")
                ?.removePrefix("MUSIC_")
                ?.takeIf { it.isNotBlank() }
            ?: return@forEach

        val rawCover = song.firstStringOf("web_albumpic_short", "web_albumpic_big", "picUrl", "pic").orEmpty()
        val coverUrl = normalizeKuwoAlbumCoverUrl(rawCover)
        if (coverUrl.isNotBlank()) {
            coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal data class ExtractedTrackComments(
    val totalCount: Int = 0,
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
}

internal fun extractNeteaseTrackComments(data: JsonElement?): ExtractedTrackComments {
    val root = data as? JsonObject ?: return ExtractedTrackComments()
    val hotComments = ((root.getIgnoreCase("hotComments") as? JsonArray).orEmpty())
        .mapNotNull { parseNeteaseTrackComment(it as? JsonObject) }
        .distinctBy(TrackComment::id)
    val latestComments = ((root.getIgnoreCase("comments") as? JsonArray).orEmpty())
        .mapNotNull { parseNeteaseTrackComment(it as? JsonObject) }
        .distinctBy(TrackComment::id)

    val knownCount = maxOf(hotComments.size, latestComments.size).toLong()
    val total = (root.firstLongOf("total") ?: knownCount)
        .coerceAtLeast(knownCount)
        .toInt()

    return ExtractedTrackComments(
        totalCount = total,
        hotComments = hotComments,
        latestComments = latestComments,
        recommendedComments = hotComments
    )
}

internal data class ExtractedCommentPage(
    val totalCount: Int = 0,
    val comments: List<TrackComment> = emptyList()
)

internal fun extractNeteaseSortedTrackComments(data: JsonElement?): ExtractedCommentPage {
    val root = data as? JsonObject ?: return ExtractedCommentPage()
    val commentRoot = (root.getIgnoreCase("data") as? JsonObject) ?: root
    val rawComments = commentRoot.getIgnoreCase("comments") ?: root.getIgnoreCase("comments")
    val commentList = when (rawComments) {
        is JsonArray -> rawComments
        is JsonObject -> (rawComments.getIgnoreCase("list") as? JsonArray)
            ?: (rawComments.getIgnoreCase("comments") as? JsonArray)
        else -> null
    } ?: JsonArray(emptyList())

    val comments = commentList
        .mapNotNull { parseNeteaseTrackComment(it as? JsonObject) }
        .distinctBy(TrackComment::id)
    val total = listOf(commentRoot, root)
        .firstNotNullOfOrNull { node -> node.firstLongOf("totalCount", "total", "commentCount") }
        ?.coerceAtLeast(comments.size.toLong())
        ?.toInt()
        ?: comments.size

    return ExtractedCommentPage(
        totalCount = total,
        comments = comments
    )
}

internal fun extractQqTrackComments(rawResponse: String, json: Json): ExtractedTrackComments? {
    val root = parseJsonObjectFromRaw(rawResponse, json) ?: return null
    val code = extractApiCode(root)
    if (code != null && code != 0 && code != 200) return null

    val commentRoot = (root.getIgnoreCase("comment") as? JsonObject)
        ?: (root.getIgnoreCase("data") as? JsonObject)
        ?: root

    val rawCommentList = commentRoot.getIgnoreCase("commentlist") ?: root.getIgnoreCase("commentlist")
    val commentList = when (rawCommentList) {
        is JsonArray -> rawCommentList
        is JsonObject -> (rawCommentList.getIgnoreCase("list") as? JsonArray)
            ?: (rawCommentList.getIgnoreCase("commentlist") as? JsonArray)
        else -> null
    } ?: JsonArray(emptyList())

    val comments = commentList.mapNotNull { item ->
        parseQqTrackComment(item as? JsonObject)
    }
    val total = listOf(commentRoot, root)
        .firstNotNullOfOrNull { node -> node.firstLongOf("commenttotal", "commentTotal", "total") }
        ?.coerceAtLeast(comments.size.toLong())
        ?.toInt()
        ?: comments.size

    return ExtractedTrackComments(
        totalCount = total,
        latestComments = comments
    )
}

internal fun extractFirstQqSongId(data: JsonElement?): String? {
    val root = data as? JsonObject ?: return null
    val candidates = buildList {
        add(root.getIgnoreCase("data"))
        add(root.getIgnoreCase("songlist"))
        add((root.getIgnoreCase("song") as? JsonObject)?.getIgnoreCase("data"))
    }

    candidates.forEach { container ->
        val items = container as? JsonArray ?: return@forEach
        items.forEach { item ->
            val songId = (item as? JsonObject)
                ?.firstStringOf("id", "songid", "songId")
                ?.takeIf { it.isNotBlank() }
            if (songId != null) return songId
        }
    }

    return null
}

internal fun extractNeteaseMvId(data: JsonElement?): String? {
    val song = ((data as? JsonObject)?.getIgnoreCase("songs") as? JsonArray)
        ?.firstOrNull() as? JsonObject ?: return null
    val mvValue = song.getIgnoreCase("mv")
    val mvObject = mvValue as? JsonObject
    return mvObject?.firstStringOf("id", "mvId", "mvid")
        ?.takeIf { it.isNotBlank() && it != "0" }
        ?: song.firstStringOf("mv", "mvid")
            ?.takeIf { it.isNotBlank() && it != "0" }
}

internal fun extractNeteaseMvUrl(data: JsonElement?): String? {
    val root = data as? JsonObject ?: return null
    val dataNode = (root.getIgnoreCase("data") as? JsonObject) ?: root
    val brs = dataNode.getIgnoreCase("brs") as? JsonObject
    listOf("1080", "720", "480", "240").forEach { resolution ->
        val url = (brs?.getIgnoreCase(resolution) as? JsonPrimitive)?.contentOrNull
        if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
    }

    return dataNode.firstStringOf("url", "playUrl", "mp4Url", "hlsUrl")
        ?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?.let(::normalizeVideoLikeUrl)
        ?: extractFirstVideoLikeUrl(dataNode)?.let(::normalizeVideoLikeUrl)
}

internal fun extractQqMvVid(data: JsonElement?): String? {
    val song = ((data as? JsonObject)?.getIgnoreCase("data") as? JsonArray)
        ?.firstOrNull() as? JsonObject ?: return null
    val mv = song.getIgnoreCase("mv") as? JsonObject
    return mv?.firstStringOf("vid", "mv_id", "mvId")
        ?.takeIf { it.isNotBlank() && it != "0" }
        ?: song.firstStringOf("mvvid", "mvVid")
            ?.takeIf { it.isNotBlank() && it != "0" }
}

internal fun extractQqMvUrl(data: JsonElement?, vid: String): String? {
    val root = data as? JsonObject ?: return null
    val candidates = buildList<JsonElement?> {
        add(
            (((root.getIgnoreCase("mvUrl") as? JsonObject)
                ?.getIgnoreCase("data") as? JsonObject)
                ?.getIgnoreCase(vid))
        )
        add(
            (((root.getIgnoreCase("mvInfo") as? JsonObject)
                ?.getIgnoreCase("data") as? JsonObject)
                ?.getIgnoreCase(vid))
        )
        add(root.getIgnoreCase("mvUrl"))
    }

    candidates.forEach { candidate ->
        when (candidate) {
            is JsonArray -> candidate.forEach { nested ->
                val url = (nested as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }
                if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
            }

            is JsonObject -> {
                val directList = candidate.getIgnoreCase("freeflow_url") as? JsonArray
                directList?.forEach { item ->
                    val url = (item as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.startsWith("http", ignoreCase = true) }
                    if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
                }

                extractFirstVideoLikeUrl(candidate)?.let { return normalizeVideoLikeUrl(it) }
            }

            is JsonPrimitive -> {
                val url = candidate.contentOrNull?.takeIf { it.startsWith("http", ignoreCase = true) }
                if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
            }

            else -> Unit
        }
    }

    return null
}

private fun parseNeteaseTrackComment(comment: JsonObject?): TrackComment? {
    val item = comment ?: return null
    val content = item.firstStringOf("content").orEmpty().trim()
    if (content.isBlank()) return null

    val user = item.getIgnoreCase("user") as? JsonObject
    return TrackComment(
        id = item.firstStringOf("commentId", "commentid", "id").orEmpty().ifBlank { content.hashCode().toString() },
        authorName = user?.firstStringOf("nickname", "nickName", "nick").orEmpty().ifBlank { "匿名用户" },
        content = content,
        likedCount = (item.firstLongOf("likedCount", "likedcount", "praisenum") ?: 0L).toInt(),
        timeMs = normalizeCommentTime(item.firstLongOf("time", "timeStamp")),
        avatarUrl = user?.firstStringOf("avatarUrl", "avatarurl").orEmpty()
    )
}

private fun parseQqTrackComment(comment: JsonObject?): TrackComment? {
    val item = comment ?: return null
    val content = item.firstStringOf("rootcommentcontent", "content", "middlecommentcontent").orEmpty().trim()
    if (content.isBlank()) return null

    val user = (item.getIgnoreCase("userinfo") ?: item.getIgnoreCase("user")) as? JsonObject
    return TrackComment(
        id = item.firstStringOf("rootcommentid", "commentid", "id").orEmpty().ifBlank { content.hashCode().toString() },
        authorName = user?.firstStringOf("nick", "nickname", "nickName")
            .orEmpty()
            .ifBlank { item.firstStringOf("rootcommentnick", "nick", "nickname").orEmpty().ifBlank { "QQ用户" } },
        content = content,
        likedCount = (item.firstLongOf("praisenum", "likedCount", "praiseNum") ?: 0L).toInt(),
        timeMs = normalizeCommentTime(item.firstLongOf("time", "timeStamp", "commenttime")),
        avatarUrl = normalizeQqImageUrl(
            user?.firstStringOf("avatarurl", "avatarUrl", "avatar").orEmpty()
        )
    )
}

private fun parseJsonObjectFromRaw(rawResponse: String, json: Json): JsonObject? {
    val trimmed = rawResponse.trim()
    if (trimmed.isBlank()) return null

    val payload = when {
        trimmed.startsWith("{") -> trimmed
        trimmed.startsWith("[") -> trimmed
        else -> Regex(
            pattern = """^[^(]+\((.*)\)\s*;?$""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(trimmed)?.groupValues?.getOrNull(1)
    } ?: return null

    return runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
}

private fun normalizeCommentTime(rawTime: Long?): Long {
    val value = rawTime ?: return 0L
    return if (value in 1..99_999_999_999L) value * 1000 else value
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

internal fun String.isLikelySameTitle(other: String): Boolean {
    val left = normalizeComparisonText()
    val right = other.normalizeComparisonText()
    if (left.isBlank() || right.isBlank()) return true
    return left == right || left.contains(right) || right.contains(left)
}

private fun String.isLikelySameArtist(other: String): Boolean {
    if (isBlank() || other.isBlank()) return true
    val left = normalizeComparisonText()
    val right = other.normalizeComparisonText()
    if (left.contains(right) || right.contains(left)) return true

    val leftTokens = left.split(Regex("[,，/&、|]")).map(String::trim).filter(String::isNotBlank)
    val rightTokens = right.split(Regex("[,，/&、|]")).map(String::trim).filter(String::isNotBlank)
    return leftTokens.any { token ->
        rightTokens.any { otherToken ->
            token == otherToken || token.contains(otherToken) || otherToken.contains(token)
        }
    }
}

internal fun String.normalizeComparisonText(): String {
    return lowercase()
        .replace(" ", "")
        .replace(Regex("""[\(\)（）\[\]【】《》\-—_·•.,，:：'"!?！？]"""), "")
}

internal fun mergeQqSearchResultItems(vararg groups: List<SearchResultItem>): List<SearchResultItem> {
    val mergedItems = LinkedHashMap<String, SearchResultItem>()
    groups.asList().flatten().forEach { item ->
        val key = item.id.ifBlank {
            "${item.type.name}:${item.title.normalizeComparisonText()}:${item.subtitle.normalizeComparisonText()}"
        }
        val existing = mergedItems[key]
        mergedItems[key] = if (existing == null) item else existing.mergeWith(item)
    }
    return mergedItems.values.toList()
}

private fun SearchResultItem.mergeWith(other: SearchResultItem): SearchResultItem {
    return copy(
        subtitle = subtitle.ifBlank { other.subtitle },
        coverUrl = coverUrl.ifBlank { other.coverUrl },
        trackCount = if (trackCount > 0) trackCount else other.trackCount,
        extra = extra.ifBlank { other.extra }
    )
}

internal fun rankQqAlbumSearchResults(keyword: String, results: List<SearchResultItem>): List<SearchResultItem> {
    val normalizedKeyword = keyword.normalizeComparisonText()
    if (normalizedKeyword.isBlank() || results.size < 2) return results

    return results.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<SearchResultItem>> {
                scoreQqAlbumSearchResult(normalizedKeyword, it.value)
            }.thenBy { it.index }
        )
        .map { it.value }
}

private fun scoreQqAlbumSearchResult(normalizedKeyword: String, item: SearchResultItem): Int {
    val normalizedTitle = item.title.normalizeComparisonText()
    val normalizedSubtitle = item.subtitle.normalizeComparisonText()
    return when {
        normalizedTitle == normalizedKeyword -> 500
        normalizedTitle.startsWith(normalizedKeyword) -> 400
        normalizedTitle.contains(normalizedKeyword) -> 300
        normalizedSubtitle == normalizedKeyword -> 200
        normalizedSubtitle.contains(normalizedKeyword) -> 100
        else -> 0
    }
}

private fun buildQqAlbumCoverUrl(albumMid: String): String {
    if (albumMid.isBlank()) return ""
    return "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
}

private fun buildQqSingerCoverUrl(singerMid: String): String {
    if (singerMid.isBlank()) return ""
    return "https://y.qq.com/music/photo_new/T001R300x300M000$singerMid.jpg"
}

private fun normalizeQqImageUrl(rawUrl: String): String {
    val url = rawUrl.trim()
    if (url.isBlank()) return ""
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://", ignoreCase = true) -> "https://${url.removePrefix("http://")}"
        else -> url
    }
}

private fun normalizeKuwoAlbumCoverUrl(rawCover: String): String {
    if (rawCover.isBlank()) return ""
    if (rawCover.startsWith("http://", ignoreCase = true) || rawCover.startsWith("https://", ignoreCase = true)) {
        return rawCover
    }
    val cleaned = rawCover.trim().trimStart('/')
    if (cleaned.isBlank()) return ""
    return "https://img4.kuwo.cn/star/albumcover/$cleaned"
}

private fun normalizeVideoLikeUrl(rawUrl: String): String {
    if (!rawUrl.startsWith("http://", ignoreCase = true)) return rawUrl

    val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
    val host = parsed.host.lowercase()
    val shouldUpgradeToHttps = host == "wx.music.tc.qq.com" ||
        host.endsWith(".music.tc.qq.com") ||
        host.endsWith(".qqmusic.qq.com")

    return if (shouldUpgradeToHttps) {
        parsed.newBuilder().scheme("https").build().toString()
    } else {
        rawUrl
    }
}

private fun isVideoLikeKey(key: String): Boolean {
    val normalized = key.lowercase()
    return normalized.contains("mv") ||
        normalized.contains("video") ||
        normalized.contains("mp4") ||
        normalized.contains("m3u8")
}

private fun isLikelyVideoLikeUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return normalized.contains(".mp4") ||
        normalized.contains(".m3u8") ||
        normalized.contains(".webm") ||
        normalized.contains("mime=video") ||
        normalized.contains("type=mp4") ||
        normalized.contains("format=mp4")
}

private fun extractFirstVideoLikeUrl(element: JsonElement?): String? {
    return when (element) {
        is JsonObject -> {
            element.entries.firstNotNullOfOrNull { (key, value) ->
                when (value) {
                    is JsonPrimitive -> {
                        value.contentOrNull
                            ?.takeIf { it.startsWith("http", ignoreCase = true) }
                            ?.takeIf { isVideoLikeKey(key) || isLikelyVideoLikeUrl(it) }
                    }

                    else -> extractFirstVideoLikeUrl(value)
                }
            }
        }

        is JsonArray -> element.firstNotNullOfOrNull(::extractFirstVideoLikeUrl)
        is JsonPrimitive -> {
            element.contentOrNull
                ?.takeIf { it.startsWith("http", ignoreCase = true) && isLikelyVideoLikeUrl(it) }
        }

        else -> null
    }
}

private fun extractNeteaseArtistText(track: JsonObject): String {
    val artists = (track.getIgnoreCase("ar") ?: track.getIgnoreCase("artists")) as? JsonArray ?: return ""
    return artists.mapNotNull { artistElement ->
        (artistElement as? JsonObject)?.firstStringOf("name")
    }.joinToString("/")
}

    private fun extractNeteaseTrack(track: JsonObject?): Track? {
        val item = track ?: return null
        val id = item.firstStringOf("id").orEmpty()
        if (id.isBlank()) return null

        val album = (item.getIgnoreCase("al") ?: item.getIgnoreCase("album")) as? JsonObject
        return Track(
            id = id,
            platform = Platform.NETEASE,
            title = item.firstStringOf("name", "title").orEmpty(),
            artist = extractNeteaseArtistText(item),
            album = album?.firstStringOf("name").orEmpty(),
            albumId = album?.firstStringOf("id", "albumId", "albumID").orEmpty(),
            coverUrl = album?.firstStringOf("picUrl", "blurPicUrl").orEmpty(),
            durationMs = item.firstLongOf("dt", "duration", "durationMs") ?: 0L
        )
    }

private fun extractApiCode(data: JsonElement?): Int? {
    val root = data as? JsonObject ?: return null
    return (root.getIgnoreCase("code") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}

private fun extractApiMessage(data: JsonElement?): String {
    val root = data as? JsonObject ?: return ""
    return root.firstStringOf("message", "msg").orEmpty()
}

internal fun shareRefererFor(url: String): String? {
    val normalizedUrl = url.lowercase()
    return when {
        "y.qq.com" in normalizedUrl -> "https://y.qq.com/"
        "music.163.com" in normalizedUrl || "163cn.tv" in normalizedUrl -> "https://music.163.com/"
        "kuwo.cn" in normalizedUrl -> "https://www.kuwo.cn/"
        else -> null
    }
}

internal fun extractShareTarget(rawContent: String): String? {
    if (rawContent.isBlank()) return null

    val variants = buildList {
        add(rawContent)

        val unescaped = rawContent
            .replace("\\/", "/")
            .replace("&amp;", "&")
        if (unescaped != rawContent) add(unescaped)

        val decoded = runCatching {
            java.net.URLDecoder.decode(unescaped, java.nio.charset.StandardCharsets.UTF_8.toString())
        }.getOrNull()
        decoded?.takeIf { it != unescaped }?.let(::add)
    }

    val prioritizedUrls = variants.asSequence()
        .flatMap { ShareUtils.extractShareUrlCandidates(it).asSequence() }
        .filter(::isSupportedShareUrl)
        .distinct()
        .sortedByDescending(::shareTargetPriority)
        .toList()
    prioritizedUrls.firstOrNull()?.let { return it }

    return variants.asSequence()
        .mapNotNull { candidate ->
            Regex(
                """(?:playlist(?:_id)?|listid|id|disstid|pid)\s*[:=]\s*["']?(\d{5,})""",
                RegexOption.IGNORE_CASE
            ).find(candidate)?.groupValues?.getOrNull(1)
        }
        .firstOrNull()
}

private fun isSupportedShareUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return "y.qq.com" in normalized ||
        "music.163.com" in normalized ||
        "163cn.tv" in normalized ||
        "kuwo.cn" in normalized
}

private fun shareTargetPriority(url: String): Int {
    val normalized = url.lowercase()
    return when {
        Regex("""(?:playlist(?:_detail)?/\d+|[?&](?:id|disstid|pid)=\d+)""").containsMatchIn(normalized) -> 3
        "163cn.tv" in normalized -> 2
        else -> 1
    }
}

private fun JsonObject.getIgnoreCase(key: String): JsonElement? {
    return this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

internal fun JsonObject.firstStringOf(vararg keys: String): String? {
    keys.forEach { key ->
        val value = (getIgnoreCase(key) as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

internal fun extractNeteaseHotSearchKeywords(data: JsonElement?): List<String> {
    val root = data as? JsonObject ?: return emptyList()
    val keywordLists = listOfNotNull(
        root.getIgnoreCase("data") as? JsonArray,
        ((root.getIgnoreCase("result") as? JsonObject)?.getIgnoreCase("hots") as? JsonArray),
        ((root.getIgnoreCase("result") as? JsonObject)?.getIgnoreCase("hot") as? JsonArray),
        root.getIgnoreCase("hots") as? JsonArray
    )

    keywordLists.forEach { keywords ->
        val parsed = keywords.mapNotNull { item ->
            when (item) {
                is JsonObject -> item.firstStringOf(
                    "searchWord", "word", "first", "keyword", "name", "content"
                )?.trim()

                is JsonPrimitive -> item.contentOrNull?.trim()
                else -> null
            }
        }.filter { it.isNotBlank() }
            .distinct()

        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}

internal fun extractNeteaseDefaultKeyword(data: JsonElement?): String? {
    val root = data as? JsonObject ?: return null
    val payload = root.getIgnoreCase("data") as? JsonObject ?: return null
    return payload.firstStringOf("realkeyword", "showKeyword", "keyword")
        ?.replace("🔥", "")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

internal fun pickNeteaseHotKeywordToplist(toplists: List<ToplistInfo>): ToplistInfo? {
    if (toplists.isEmpty()) return null
    val priorities = listOf("热歌", "飙升", "新歌", "原创", "流行")
    return priorities.firstNotNullOfOrNull { token ->
        toplists.firstOrNull { it.name.contains(token) }
    } ?: toplists.first()
}

private fun JsonObject.firstLongOf(vararg keys: String): Long? {
    keys.forEach { key ->
        val value = (getIgnoreCase(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        if (value != null) return value
    }
    return null
}

internal fun SearchType.toNeteaseOfficialSearchType(): Int = when (this) {
    SearchType.SONG -> 1
    SearchType.ARTIST -> 100
    SearchType.ALBUM -> 10
    SearchType.PLAYLIST -> 1000
}

private fun SearchType.toQqOfficialSearchType(): Int = when (this) {
    SearchType.SONG -> 0
    SearchType.ARTIST -> 1
    SearchType.ALBUM -> 2
    SearchType.PLAYLIST -> 3
}

internal fun buildQqSearchByTypeBody(
    keyword: String,
    page: Int,
    pageSize: Int,
    type: SearchType
): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 11)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("search", buildJsonObject {
        put("module", "music.search.SearchCgiService")
        put("method", "DoSearchForQQMusicMobile")
        put("param", buildJsonObject {
            put("searchid", buildQqSearchId())
            put("query", keyword)
            put("search_type", type.toQqOfficialSearchType())
            put("page_num", page.coerceAtLeast(1))
            put("num_per_page", pageSize.coerceIn(1, 50))
            put("highlight", 1)
            put("nqc_flag", 0)
            put("grp", 1)
        })
    })
}

internal fun buildQqGeneralSearchBody(
    keyword: String,
    page: Int,
    pageSize: Int
): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("req", buildJsonObject {
        put("method", "do_search_v2")
        put("module", "music.adaptor.SearchAdaptor")
        put("param", buildJsonObject {
            put("query", keyword)
            put("search_type", 100)
            put("page_num", page.coerceAtLeast(1))
            put("num_per_page", pageSize.coerceIn(1, 50))
            put("highlight", true)
            put("grp", 1)
        })
    })
}

internal fun buildQqMvRequestBody(vid: String): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("mvInfo", buildJsonObject {
        put("module", "video.VideoDataServer")
        put("method", "getMVInfo")
        put("param", buildJsonObject {
            put("vidlist", JsonArray(listOf(JsonPrimitive(vid))))
        })
    })
    put("mvUrl", buildJsonObject {
        put("module", "music.stream.MvUrlProxy")
        put("method", "GetMvUrls")
        put("param", buildJsonObject {
            put("vids", JsonArray(listOf(JsonPrimitive(vid))))
            put("request_typet", 10001)
            put("addrtype", 3)
            put("format", 264)
        })
    })
}

private fun buildQqSearchId(): String {
    val now = System.currentTimeMillis()
    val suffix = (now % 1_000_000).toString().padStart(6, '0')
    return "$now$suffix"
}

internal fun extractNeteaseSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val result = ((data as? JsonObject)?.getIgnoreCase("result") as? JsonObject) ?: return emptyList()
    val items = when (type) {
        SearchType.ARTIST -> extractNestedArray(result, "artists", "artist")
        SearchType.ALBUM -> extractNestedArray(result, "albums", "album")
        SearchType.PLAYLIST -> extractNestedArray(result, "playlists", "playlist")
        SearchType.SONG -> null
    } ?: return emptyList()

    return items.mapNotNull { element ->
        when (type) {
            SearchType.ARTIST -> parseNeteaseArtistSearchItem(element as? JsonObject)
            SearchType.ALBUM -> parseNeteaseAlbumSearchItem(element as? JsonObject)
            SearchType.PLAYLIST -> parseNeteasePlaylistSearchItem(element as? JsonObject)
            SearchType.SONG -> null
        }
    }
}

internal fun extractQqSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val body = extractQqSearchBody(data) ?: return emptyList()
    val items = when (type) {
        SearchType.ARTIST -> extractQqSearchItems(
            body,
            "singer", "item_singer", "zhida", "singerlist", "itemlist", "list", "zhida_singer"
        )

        SearchType.ALBUM -> extractQqSearchItems(
            body,
            "album", "item_album", "albumlist", "itemlist", "list"
        )

        SearchType.PLAYLIST -> extractQqSearchItems(
            body,
            "songlist", "item_songlist", "playlist", "itemlist", "list"
        )

        SearchType.SONG -> emptyList()
    }

    return items.mapNotNull { element ->
        when (type) {
            SearchType.ARTIST -> parseQqArtistSearchItem(element as? JsonObject)
            SearchType.ALBUM -> parseQqAlbumSearchItem(element as? JsonObject)
            SearchType.PLAYLIST -> parseQqPlaylistSearchItem(element as? JsonObject)
            SearchType.SONG -> null
        }
    }
}

internal fun extractQqDirectSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val body = extractQqSearchBody(data) ?: return emptyList()
    val items = (((body.getIgnoreCase("direct_result") as? JsonObject)
        ?.getIgnoreCase("items")) as? JsonArray).orEmpty()

    val restype = when (type) {
        SearchType.ARTIST -> "singer"
        SearchType.ALBUM -> "album"
        SearchType.PLAYLIST -> "songlist"
        SearchType.SONG -> return emptyList()
    }

    return items.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        if (!item.firstStringOf("restype").orEmpty().equals(restype, ignoreCase = true)) {
            return@mapNotNull null
        }
        when (type) {
            SearchType.ARTIST -> parseQqArtistSearchItem(item)
            SearchType.ALBUM -> parseQqAlbumSearchItem(item)
            SearchType.PLAYLIST -> parseQqPlaylistSearchItem(item)
            SearchType.SONG -> null
        }
    }
}

internal fun extractQqSingerAlbumSearchResults(data: JsonElement?): List<SearchResultItem> {
    val albumList = (((data as? JsonObject)?.getIgnoreCase("albums") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject)
        ?.getIgnoreCase("albumList") as? JsonArray ?: return emptyList()

    return albumList.mapNotNull { element ->
        parseQqAlbumSearchItem(element as? JsonObject)
    }
}

internal fun extractQqAlbumSearchResultsFromSongs(data: JsonElement?): List<SearchResultItem> {
    val body = extractQqSearchBody(data) ?: return emptyList()
    val songNode = body.getIgnoreCase("item_song") ?: body.getIgnoreCase("song") ?: return emptyList()
    val songItems = extractQqSearchItems(songNode, "items", "list")
    return songItems.mapNotNull { element ->
        parseQqAlbumSearchItemFromSong(element as? JsonObject)
    }
}

internal fun extractKuwoSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val root = data as? JsonObject ?: return emptyList()
    val container = root.getIgnoreCase("data") ?: root
    val items = when (type) {
        SearchType.ARTIST -> extractNestedArray(container, "artistList", "artistlist", "list")
        SearchType.ALBUM -> extractNestedArray(container, "albumList", "albumlist", "list")
        SearchType.PLAYLIST -> extractNestedArray(container, "playList", "playlist", "playlists", "list")
        SearchType.SONG -> null
    } ?: return emptyList()

    return items.mapNotNull { element ->
        when (type) {
            SearchType.ARTIST -> parseKuwoArtistSearchItem(element as? JsonObject)
            SearchType.ALBUM -> parseKuwoAlbumSearchItem(element as? JsonObject)
            SearchType.PLAYLIST -> parseKuwoPlaylistSearchItem(element as? JsonObject)
            SearchType.SONG -> null
        }
    }
}

private fun parseNeteaseArtistSearchItem(item: JsonObject?): SearchResultItem? {
    val artist = item ?: return null
    val id = artist.firstStringOf("id") ?: return null
    val name = artist.firstStringOf("name")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        coverUrl = artist.firstStringOf("picUrl", "img1v1Url", "coverImgUrl").orEmpty(),
        platform = Platform.NETEASE,
        type = SearchType.ARTIST,
        trackCount = artist.firstLongOf("musicSize", "songCount", "trackCount").toSearchCount()
    )
}

private fun parseNeteaseAlbumSearchItem(item: JsonObject?): SearchResultItem? {
    val album = item ?: return null
    val id = album.firstStringOf("id") ?: return null
    val name = album.firstStringOf("name")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = extractJoinedNames(album.getIgnoreCase("artists") ?: album.getIgnoreCase("artist")),
        coverUrl = album.firstStringOf("picUrl", "blurPicUrl", "coverImgUrl").orEmpty(),
        platform = Platform.NETEASE,
        type = SearchType.ALBUM,
        trackCount = album.firstLongOf("size", "songCount", "trackCount").toSearchCount()
    )
}

private fun parseNeteasePlaylistSearchItem(item: JsonObject?): SearchResultItem? {
    val playlist = item ?: return null
    val id = playlist.firstStringOf("id") ?: return null
    val name = playlist.firstStringOf("name")?.trim().orEmpty()
    if (name.isBlank()) return null

    val creator = playlist.getIgnoreCase("creator") as? JsonObject
    return SearchResultItem(
        id = id,
        title = name,
        subtitle = creator?.firstStringOf("nickname").orEmpty(),
        coverUrl = playlist.firstStringOf("coverImgUrl", "picUrl", "imgUrl").orEmpty(),
        platform = Platform.NETEASE,
        type = SearchType.PLAYLIST,
        trackCount = playlist.firstLongOf("trackCount", "songCount", "count").toSearchCount()
    )
}

private fun parseQqArtistSearchItem(item: JsonObject?): SearchResultItem? {
    val artist = item ?: return null
    val customInfo = artist.getIgnoreCase("custom_info") as? JsonObject
    val id = customInfo?.firstStringOf("mid", "singer_mid")
        ?: artist.firstStringOf("singerMID", "singerMid", "singer_mid", "singerid", "mid", "id")
        ?: return null
    val name = artist.firstStringOf("singerName", "singer_name", "name", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        coverUrl = normalizeQqImageUrl(artist.firstStringOf("singerPic", "pic", "avatar", "imgurl").orEmpty()),
        platform = Platform.QQ,
        type = SearchType.ARTIST,
        trackCount = (
            customInfo?.firstLongOf("song_num", "songNum")
                ?: artist.firstLongOf("songNum", "songnum", "musicNum", "song_count", "totalSongNum")
            ).toSearchCount()
    )
}

private fun parseQqAlbumSearchItem(item: JsonObject?): SearchResultItem? {
    val album = item ?: return null
    val customInfo = album.getIgnoreCase("custom_info") as? JsonObject
    val albumMid = customInfo?.firstStringOf("mid")
        ?: album.firstStringOf("albumMID", "albumMid", "albummid", "mid")
    val id = album.firstStringOf("albumID", "albumId", "id") ?: albumMid ?: return null
    val name = album.firstStringOf("albumName", "album_name", "name", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    val subtitle = album.firstStringOf("singerName", "singer_name", "artistName", "artist", "singer").orEmpty()
        .ifBlank {
            customInfo?.firstStringOf("quality_album_title_prefix")
                .orEmpty()
        }.ifBlank {
            extractJoinedNames(album.getIgnoreCase("singerList") ?: album.getIgnoreCase("singer"))
        }
    val coverUrl = album.firstStringOf("albumPic", "pic", "imgurl")
        ?.let(::normalizeQqImageUrl)
        .orEmpty()
        .ifBlank { buildQqAlbumCoverUrl(albumMid.orEmpty()) }

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = subtitle,
        coverUrl = coverUrl,
        platform = Platform.QQ,
        type = SearchType.ALBUM,
        trackCount = (customInfo?.firstLongOf("track_num")
            ?: album.firstLongOf("song_count", "songNum", "songnum", "musicNum", "trackCount", "totalNum")).toSearchCount()
    )
}

private fun parseQqAlbumSearchItemFromSong(item: JsonObject?): SearchResultItem? {
    val song = item ?: return null
    val album = song.getIgnoreCase("album") as? JsonObject ?: return null
    val albumMid = album.firstStringOf("mid", "albumMID", "albumMid", "albummid")
    val id = album.firstStringOf("id", "albumID", "albumId") ?: albumMid ?: return null
    val name = album.firstStringOf("name", "title", "albumName")?.trim().orEmpty()
    if (name.isBlank()) return null

    val subtitle = extractJoinedNames(song.getIgnoreCase("singer") ?: song.getIgnoreCase("singerList"))
        .ifBlank { song.firstStringOf("singerName", "artistName").orEmpty() }

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = subtitle,
        coverUrl = buildQqAlbumCoverUrl(albumMid.orEmpty()),
        platform = Platform.QQ,
        type = SearchType.ALBUM
    )
}

private fun parseQqPlaylistSearchItem(item: JsonObject?): SearchResultItem? {
    val playlist = item ?: return null
    val id = playlist.firstStringOf("dissid", "tid", "id") ?: return null
    val name = playlist.firstStringOf("dissname", "diss_name", "name", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = playlist.firstStringOf("creatorName", "nick", "nickname").orEmpty(),
        coverUrl = normalizeQqImageUrl(
            playlist.firstStringOf("imgurl", "cover_url_big", "coverUrl", "logo").orEmpty()
        ),
        platform = Platform.QQ,
        type = SearchType.PLAYLIST,
        trackCount = playlist.firstLongOf("song_count", "songNum", "songnum", "total_song_num").toSearchCount()
    )
}

private fun parseKuwoArtistSearchItem(item: JsonObject?): SearchResultItem? {
    val artist = item ?: return null
    val id = artist.firstStringOf("id", "artistid", "artistId") ?: return null
    val name = artist.firstStringOf("name", "artist", "artistName")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        coverUrl = normalizeQqImageUrl(artist.firstStringOf("pic", "htsPic", "pic300").orEmpty()),
        platform = Platform.KUWO,
        type = SearchType.ARTIST,
        trackCount = artist.firstLongOf("musicNum", "songNum", "songnum", "trackCount").toSearchCount()
    )
}

private fun parseKuwoAlbumSearchItem(item: JsonObject?): SearchResultItem? {
    val album = item ?: return null
    val id = album.firstStringOf("albumid", "albumId", "id") ?: return null
    val name = album.firstStringOf("album", "name", "albumName", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = album.firstStringOf("artist", "artistName", "singer", "artist_name").orEmpty()
            .ifBlank {
                extractJoinedNames(album.getIgnoreCase("artistList") ?: album.getIgnoreCase("artists"))
            },
        coverUrl = normalizeKuwoSearchCoverUrl(
            album.firstStringOf("pic", "img", "albumPic", "picUrl", "htsPic").orEmpty()
        ),
        platform = Platform.KUWO,
        type = SearchType.ALBUM,
        trackCount = album.firstLongOf("songnum", "songNum", "musicNum", "trackCount", "count").toSearchCount()
    )
}

private fun parseKuwoPlaylistSearchItem(item: JsonObject?): SearchResultItem? {
    val playlist = item ?: return null
    val id = playlist.firstStringOf("id", "playlistid", "pid") ?: return null
    val name = playlist.firstStringOf("name", "playlist", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = playlist.firstStringOf("uname", "nickName", "nickname").orEmpty(),
        coverUrl = normalizeKuwoSearchCoverUrl(
            playlist.firstStringOf("img", "pic", "imgurl", "coverUrl").orEmpty()
        ),
        platform = Platform.KUWO,
        type = SearchType.PLAYLIST,
        trackCount = playlist.firstLongOf("musicNum", "songNum", "songnum", "trackCount", "count").toSearchCount()
    )
}

private fun extractQqSearchBody(data: JsonElement?): JsonObject? {
    val root = data as? JsonObject ?: return null
    val candidates = buildList {
        add(root)
        root.values.forEach { value ->
            if (value is JsonObject) add(value)
        }
    }

    candidates.forEach { candidate ->
        (candidate.getIgnoreCase("body") as? JsonObject)?.let { return it }
        val dataNode = candidate.getIgnoreCase("data") as? JsonObject ?: return@forEach
        (dataNode.getIgnoreCase("body") as? JsonObject)?.let { return it }
    }
    return null
}

private fun extractQqSearchItems(node: JsonElement?, vararg preferredKeys: String): List<JsonElement> {
    return when (node) {
        is JsonArray -> node.toList()
        is JsonObject -> {
            preferredKeys.forEach { key ->
                val child = node.getIgnoreCase(key) ?: return@forEach
                val items = extractQqSearchItems(child, *preferredKeys)
                if (items.isNotEmpty()) return items
                if (child is JsonObject && child.isQqSearchTerminalItem()) {
                    return listOf(child)
                }
            }
            if (node.isQqSearchTerminalItem()) listOf(node) else emptyList()
        }

        else -> emptyList()
    }
}

private fun JsonObject.isQqSearchTerminalItem(): Boolean {
    return firstStringOf(
        "singerMID", "singerMid", "singer_mid", "albumMID", "albumMid",
        "albummid", "dissid", "tid", "mid", "id"
    ) != null
}

private fun extractNestedArray(node: JsonElement?, vararg preferredKeys: String): JsonArray? {
    return when (node) {
        is JsonArray -> node
        is JsonObject -> {
            preferredKeys.firstNotNullOfOrNull { key ->
                extractNestedArray(node.getIgnoreCase(key), *preferredKeys)
            }
        }

        else -> null
    }
}

private fun extractJoinedNames(node: JsonElement?): String {
    return when (node) {
        is JsonArray -> node.mapNotNull { item ->
            when (item) {
                is JsonObject -> item.firstStringOf("name", "artistName", "singerName", "title")
                is JsonPrimitive -> item.contentOrNull
                else -> null
            }?.trim()?.takeIf(String::isNotBlank)
        }.distinct().joinToString("/")

        is JsonObject -> node.firstStringOf("name", "artistName", "singerName", "title").orEmpty()
        is JsonPrimitive -> node.contentOrNull.orEmpty()
        else -> ""
    }
}

private fun extractTextList(node: JsonElement?, vararg objectKeys: String): List<String> {
    val candidateKeys = if (objectKeys.isEmpty()) {
        listOf("name", "title", "label", "text")
    } else {
        objectKeys.toList()
    }

    return when (node) {
        is JsonArray -> node.flatMap { child ->
            extractTextList(child, *candidateKeys.toTypedArray())
        }

        is JsonObject -> candidateKeys.firstNotNullOfOrNull { key ->
            node.firstStringOf(key)
        }?.let(::listOf).orEmpty()

        is JsonPrimitive -> node.contentOrNull
            ?.split(',', '，', '、')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

        else -> emptyList()
    }.distinct()
}

private fun Long?.toSearchCount(): Int {
    val value = this ?: return 0
    return value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

private fun normalizeKuwoSearchCoverUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("//")
    ) {
        normalizeQqImageUrl(trimmed)
    } else {
        normalizeKuwoAlbumCoverUrl(trimmed)
    }
}

// ── Artist / Playlist Browse helpers ──

private fun buildQqArtistDetailBody(singerMid: String): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("songs", buildJsonObject {
        put("module", "musichall.song_list_server")
        put("method", "GetSingerSongList")
        put("param", buildJsonObject {
            put("singerMid", singerMid)
            put("order", 1)
            put("begin", 0)
            put("num", 50)
        })
    })
    put("albums", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumListServer")
        put("method", "GetAlbumList")
        put("param", buildJsonObject {
            put("singerMid", singerMid)
            put("order", 1)
            put("begin", 0)
            put("num", 20)
        })
    })
}

internal fun buildQqSingerAlbumsBody(singerMid: String, page: Int, pageSize: Int): JsonElement = buildJsonObject {
    val safePage = page.coerceAtLeast(1)
    val safePageSize = pageSize.coerceIn(1, 50)
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("albums", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumListServer")
        put("method", "GetAlbumList")
        put("param", buildJsonObject {
            put("singerMid", singerMid)
            put("order", 1)
            put("begin", (safePage - 1) * safePageSize)
            put("num", safePageSize)
        })
    })
}

private const val QQ_DEFAULT_PLAYLIST_CATEGORY_ID = 6
private const val QQ_ALBUM_DETAIL_PAGE_SIZE = 300

private fun buildQqAlbumInfoBody(idOrMid: String): JsonElement = buildJsonObject {
    val numericAlbumId = idOrMid.toLongOrNull()
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("album", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumInfoServer")
        put("method", "GetAlbumDetail")
        put("param", buildJsonObject {
            if (numericAlbumId != null) {
                put("albumId", numericAlbumId)
            } else {
                put("albumMid", idOrMid)
            }
        })
    })
}

private fun buildQqAlbumSongListBody(albumId: String): JsonElement = buildJsonObject {
    val numericAlbumId = albumId.toLongOrNull()
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("songs", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumSongList")
        put("method", "GetAlbumSongList")
        put("param", buildJsonObject {
            if (numericAlbumId != null) {
                put("albumId", numericAlbumId)
            } else {
                put("albumId", albumId)
            }
            put("begin", 0)
            put("num", QQ_ALBUM_DETAIL_PAGE_SIZE)
        })
    })
}

private val qqPlaylistCategoryIdMap = linkedMapOf(
    "流行" to 6, "经典" to 22, "轻音乐" to 12, "摇滚" to 19,
    "民谣" to 8, "电子" to 14, "嘻哈" to 25, "R&B" to 17, "古典" to 7
)

private fun buildQqPlaylistByCategoryBody(category: String, page: Int, pageSize: Int): JsonElement = buildJsonObject {
    val categoryId = qqPlaylistCategoryIdMap[category] ?: QQ_DEFAULT_PLAYLIST_CATEGORY_ID
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("playlist", buildJsonObject {
        put("module", "playlist.PlayListPlazaServer")
        put("method", "get_playlist_by_category")
        put("param", buildJsonObject {
            put("id", categoryId)
            put("curPage", page.coerceAtLeast(1))
            put("size", pageSize.coerceAtLeast(1))
            put("order", 5)
            put("titleid", categoryId)
        })
    })
}

private fun extractNeteaseArtistAlbums(data: JsonElement?): List<ArtistAlbum> {
    val root = data as? JsonObject ?: return emptyList()
    val hotAlbums = root.getIgnoreCase("hotAlbums") as? JsonArray ?: return emptyList()
    return hotAlbums.mapNotNull { el ->
        val album = el as? JsonObject ?: return@mapNotNull null
        val id = album.firstStringOf("id") ?: return@mapNotNull null
        ArtistAlbum(
            id = id,
            name = album.firstStringOf("name").orEmpty(),
            coverUrl = album.firstStringOf("picUrl", "blurPicUrl").orEmpty(),
            publishTime = album.firstStringOf("publishTime").orEmpty(),
            songCount = (album.firstLongOf("size") ?: 0L).toInt()
        )
    }
}

private data class NeteaseArtistEnhancedMetadata(
    val description: String = "",
    val tags: List<String> = emptyList()
)

private suspend fun fetchNeteaseEnhancedArtistMetadata(
    artistId: String,
    preferences: PlayerPreferences?,
    enhancedApi: NeteaseCloudApiEnhancedApi?
): NeteaseArtistEnhancedMetadata? {
    val configuredPreferences = preferences ?: return null
    val configuredApi = enhancedApi ?: return null
    val baseUrl = configuredPreferences.neteaseCloudApiBaseUrl.first()
    if (baseUrl.isBlank()) return null

    val artistDetailResponse = runCatching {
        configuredApi.artistDetail(
            url = buildNeteaseEnhancedEndpoint(baseUrl, "artist/detail"),
            id = artistId
        )
    }.getOrNull()

    val artistDescResponse = runCatching {
        configuredApi.artistDesc(
            url = buildNeteaseEnhancedEndpoint(baseUrl, "artist/desc"),
            id = artistId
        )
    }.getOrNull()

    if (artistDetailResponse == null && artistDescResponse == null) return null

    return NeteaseArtistEnhancedMetadata(
        description = extractNeteaseEnhancedArtistDescription(artistDescResponse),
        tags = extractNeteaseEnhancedArtistTags(artistDetailResponse)
    )
}

private fun buildNeteaseEnhancedEndpoint(baseUrl: String, path: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return "$trimmed/$path"
}

private fun extractNeteaseEnhancedArtistDescription(response: JsonElement?): String {
    val root = response as? JsonObject ?: return ""
    val code = extractApiCode(root)
    if (code != null && code != 200) return ""

    val briefDesc = root.firstStringOf("briefDesc").orEmpty().trim()
    val introduction = (root.getIgnoreCase("introduction") as? JsonArray)
        ?.mapNotNull { section ->
            val obj = section as? JsonObject ?: return@mapNotNull null
            listOf(
                obj.firstStringOf("ti", "title").orEmpty().trim(),
                obj.firstStringOf("txt", "text").orEmpty().trim()
            ).filter(String::isNotBlank).joinToString("\n")
                .takeIf(String::isNotBlank)
        }
        .orEmpty()
        .distinct()

    return listOfNotNull(
        briefDesc.takeIf(String::isNotBlank),
        introduction.joinToString("\n\n").takeIf(String::isNotBlank)
    ).distinct().joinToString("\n\n")
}

private fun extractNeteaseEnhancedArtistTags(response: JsonElement?): List<String> {
    val root = response as? JsonObject ?: return emptyList()
    val code = extractApiCode(root)
    if (code != null && code != 200) return emptyList()

    val data = root.getIgnoreCase("data") as? JsonObject ?: return emptyList()
    val artist = data.getIgnoreCase("artist") as? JsonObject
    val identify = data.getIgnoreCase("identify") as? JsonObject
    val secondaryIdentity = data.getIgnoreCase("secondaryExpertIdentiy")
        ?: data.getIgnoreCase("secondaryExpertIdentity")

    return buildList {
        addAll(extractTextList(artist?.getIgnoreCase("identities")))
        addAll(extractTextList(artist?.getIgnoreCase("musicIdentityTags")))
        addAll(extractTextList(identify?.getIgnoreCase("imageDesc")))
        addAll(extractTextList(secondaryIdentity, "expertIdentiyName", "expertIdentityName", "name", "title"))
    }.map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}

private fun extractQqArtistSong(song: JsonObject?): Track? {
    val item = ((song?.getIgnoreCase("songInfo")) ?: song) as? JsonObject ?: return null
    val mid = item.firstStringOf("mid", "songmid") ?: return null
    val singers = item.get("singer") as? JsonArray
    val artist = singers?.mapNotNull { (it as? JsonObject)?.firstStringOf("name") }?.joinToString("/").orEmpty()
    val albumObj = item.get("album") as? JsonObject
    val albumMid = albumObj?.firstStringOf("mid").orEmpty()
    return Track(
        id = mid,
        platform = Platform.QQ,
        title = item.firstStringOf("name", "title").orEmpty(),
        artist = artist,
        album = albumObj?.firstStringOf("name").orEmpty(),
        albumId = albumObj?.firstStringOf("id", "albumID", "albumId", "mid", "albumMID", "albumMid", "albummid").orEmpty(),
        coverUrl = buildQqAlbumCoverUrl(albumMid),
        durationMs = (item.firstLongOf("interval") ?: 0L) * 1000
    )
}

private fun extractQqAlbumInfoData(data: JsonElement?): JsonObject? {
    return (((data as? JsonObject)?.get("album") as? JsonObject)
        ?.get("data") as? JsonObject)
}

private fun extractQqAlbumIdFromInfoData(albumData: JsonObject?): String? {
    val basicInfo = albumData?.getIgnoreCase("basicInfo") as? JsonObject ?: return null
    return basicInfo.firstStringOf("albumID", "albumId", "id")
}

private fun extractQqAlbumId(data: JsonElement?): String? =
    extractQqAlbumIdFromInfoData(extractQqAlbumInfoData(data))

private fun extractQqAlbumTracks(data: JsonElement?): List<Track> {
    val songs = ((((data as? JsonObject)?.get("songs") as? JsonObject)
        ?.get("data") as? JsonObject)
        ?.get("songList") as? JsonArray) ?: return emptyList()
    return songs.mapNotNull { extractQqArtistSong(it as? JsonObject) }
}

private fun extractQqArtistAlbum(album: JsonObject?): ArtistAlbum? {
    val item = album ?: return null
    val id = item.firstStringOf("albumID", "albumId", "id") ?: return null
    val albumMid = item.firstStringOf("albumMid", "albumMID", "mid").orEmpty()
    return ArtistAlbum(
        id = id,
        name = item.firstStringOf("albumName", "name", "title").orEmpty(),
        coverUrl = buildQqAlbumCoverUrl(albumMid),
        publishTime = item.firstStringOf("publishDate", "public_time", "time_public").orEmpty(),
        songCount = (item.firstLongOf("totalNum", "track_num", "song_count") ?: 0L).toInt()
    )
}
