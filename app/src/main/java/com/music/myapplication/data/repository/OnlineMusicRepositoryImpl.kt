package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.network.dispatch.DispatchExecutor
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LyricsResult
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.TrackComment
import com.music.myapplication.domain.repository.TrackCommentsResult
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class OnlineMusicRepositoryImpl @Inject constructor(
    private val api: TuneHubApi,
    private val okHttpClient: OkHttpClient,
    private val dispatchExecutor: DispatchExecutor,
    private val json: Json,
    private val homeContentCacheStore: HomeContentCacheStore
) : OnlineMusicRepository {

    private val neteaseCoverCache = ConcurrentHashMap<String, String>()
    private val qqCoverCache = ConcurrentHashMap<String, String>()
    private val kuwoCoverCache = ConcurrentHashMap<String, String>()

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
        return enrichTrackCoverIfNeeded(platform, result)
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
        val enrichedTracks = enrichTrackCoverIfNeeded(platform, tracks)
        if (platform != Platform.QQ || enrichedTracks.none { it.coverUrl.isBlank() }) {
            return enrichedTracks
        }

        val coverMap = runCatching {
            fetchQqToplistSongCoverMap(id)
        }.getOrDefault(emptyMap())

        if (coverMap.isEmpty()) return enrichedTracks
        return fillMissingTrackCovers(enrichedTracks, coverMap)
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
        return enrichTrackCoverIfNeeded(platform, result)
    }

    override suspend fun resolveShareUrl(url: String): String = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return@withContext normalizedUrl

        runCatching {
            val requestBuilder = Request.Builder().url(normalizedUrl).get()
            shareRefererFor(normalizedUrl)?.let { referer ->
                requestBuilder.header("Referer", referer)
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val responseBody = response.body?.string().orEmpty()
                when {
                    finalUrl.isNotBlank() && !finalUrl.equals(normalizedUrl, ignoreCase = true) -> finalUrl
                    else -> extractShareTarget(responseBody) ?: finalUrl.ifBlank { normalizedUrl }
                }
            }
        }.getOrDefault(normalizedUrl)
    }

    override suspend fun resolvePlayableUrl(
        platform: Platform, songId: String, quality: String
    ): Result<String> {
        return try {
            val response = api.parse(
                apiKey = "", // Will be injected by interceptor
                request = ParseRequestDto(
                    platform = platform.id,
                    ids = songId,
                    quality = quality
                )
            )
            if (response.code != 0) {
                Result.Error(
                    AppError.Api(
                        message = response.resolvedMessage.ifBlank { "解析接口请求失败" },
                        code = response.code
                    )
                )
            } else {
                val playableUrl = extractPlayableUrl(response.data)
                if (playableUrl.isNullOrBlank()) {
                    Result.Error(AppError.Parse(message = "解析播放地址失败"))
                } else {
                    Result.Success(playableUrl)
                }
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    override suspend fun getLyrics(platform: Platform, songId: String): Result<LyricsResult> {
        return try {
            val response = api.parse(
                apiKey = "",
                request = ParseRequestDto(platform = platform.id, ids = songId)
            )
            if (response.code != 0) {
                Result.Error(
                    AppError.Api(
                        message = response.resolvedMessage.ifBlank { "歌词接口请求失败" },
                        code = response.code
                    )
                )
            } else {
                val lyric = extractLyric(response.data)
                if (lyric.isNullOrBlank()) Result.Error(AppError.Parse(message = "歌词为空"))
                else {
                    val translation = extractTranslation(response.data)
                    Result.Success(LyricsResult(lyric = lyric, translation = translation))
                }
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

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

    private fun extractPlayableUrl(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("url", "playUrl", "play_url", "musicUrl"))
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let(::normalizePlayableUrl)
    }

    private fun extractLyric(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("lyric", "lrc", "lyrics", "lyricText"))
    }

    private fun extractTranslation(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("tlyric", "trans", "translation", "translateLyric", "transLyric"))
    }

    private fun findFirstMatch(element: JsonElement?, keys: List<String>): String? {
        return when (element) {
            is JsonObject -> {
                keys.firstNotNullOfOrNull { key ->
                    val value = element[key] ?: element.entries.firstOrNull {
                        it.key.equals(key, ignoreCase = true)
                    }?.value
                    (value as? JsonPrimitive)?.contentOrNull
                } ?: element.values.firstNotNullOfOrNull { child -> findFirstMatch(child, keys) }
            }
            is JsonArray -> element.firstNotNullOfOrNull { child -> findFirstMatch(child, keys) }
            is JsonPrimitive -> element.contentOrNull
            else -> null
        }
    }

    private fun normalizePlayableUrl(rawUrl: String): String {
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

    private suspend fun enrichTrackCoverIfNeeded(
        platform: Platform,
        result: Result<List<Track>>
    ): Result<List<Track>> {
        return when (result) {
            is Result.Success -> Result.Success(
                enrichTrackCoverIfNeeded(platform, result.data)
            )
            is Result.Error -> result
            is Result.Loading -> result
        }
    }

    private suspend fun enrichTrackCoverIfNeeded(platform: Platform, tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks
        return when (platform) {
            Platform.NETEASE -> enrichNeteaseCoverIfNeeded(tracks)
            Platform.QQ -> enrichQqCoverIfNeeded(tracks)
            Platform.KUWO -> enrichKuwoCoverIfNeeded(tracks)
        }
    }

    private suspend fun enrichNeteaseCoverIfNeeded(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks

        val missingCoverIds = tracks
            .asSequence()
            .filter { it.coverUrl.isBlank() && it.id.isDigitsOnly() }
            .map { it.id }
            .distinct()
            .toList()
        if (missingCoverIds.isEmpty()) return tracks

        val idsToFetch = missingCoverIds.filter { neteaseCoverCache[it].isNullOrBlank() }
        if (idsToFetch.isNotEmpty()) {
            runCatching {
                fetchNeteaseSongCovers(idsToFetch)
            }.getOrDefault(emptyMap())
                .forEach { (songId, coverUrl) ->
                    if (coverUrl.isNotBlank()) {
                        neteaseCoverCache[songId] = coverUrl
                    }
                }
        }

        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = neteaseCoverCache[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun enrichQqCoverIfNeeded(tracks: List<Track>): List<Track> {
        val missingCoverIds = tracks
            .asSequence()
            .filter { it.coverUrl.isBlank() && it.id.isNotBlank() }
            .map { it.id }
            .distinct()
            .toList()
        if (missingCoverIds.isEmpty()) return tracks

        val idsToFetch = missingCoverIds.filter { qqCoverCache[it].isNullOrBlank() }
        if (idsToFetch.isNotEmpty()) {
            runCatching {
                fetchQqSongCovers(idsToFetch)
            }.getOrDefault(emptyMap())
                .forEach { (songId, coverUrl) ->
                    if (coverUrl.isNotBlank()) {
                        qqCoverCache[songId] = coverUrl
                    }
                }
        }

        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = qqCoverCache[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun enrichKuwoCoverIfNeeded(tracks: List<Track>): List<Track> {
        val missingCoverIds = tracks
            .asSequence()
            .filter { it.coverUrl.isBlank() && it.id.isDigitsOnly() }
            .map { it.id }
            .distinct()
            .toList()
        if (missingCoverIds.isEmpty()) return tracks

        val idsToFetch = missingCoverIds.filter { kuwoCoverCache[it].isNullOrBlank() }
        if (idsToFetch.isNotEmpty()) {
            runCatching {
                fetchKuwoSongCovers(idsToFetch)
            }.getOrDefault(emptyMap())
                .forEach { (songId, coverUrl) ->
                    if (coverUrl.isNotBlank()) {
                        kuwoCoverCache[songId] = coverUrl
                    }
                }
        }

        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = kuwoCoverCache[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun fetchNeteaseSongCovers(songIds: List<String>): Map<String, String> {
        if (songIds.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        songIds.chunked(NETEASE_DETAIL_BATCH_SIZE).forEach { chunk ->
            val idsParam = chunk.joinToString(prefix = "[", postfix = "]")
            val response = api.getNeteaseSongDetail(ids = idsParam)
            result.putAll(extractNeteaseSongCoverMap(response))
        }
        return result
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

            Result.Success(enrichTrackCoverIfNeeded(Platform.NETEASE, tracks))
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
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

    private suspend fun fetchQqSongCovers(songMids: List<String>): Map<String, String> {
        if (songMids.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        songMids.chunked(QQ_DETAIL_BATCH_SIZE).forEach { chunk ->
            val midsParam = chunk.joinToString(",")
            val response = api.getQqSongDetail(songMid = midsParam)
            result.putAll(extractQqSongCoverMap(response))
        }
        return result
    }

    private suspend fun fetchQqToplistsDirect(): List<ToplistInfo> {
        val response = api.postQqMusicu(body = buildQqToplistsRequestBody())
        return extractQqToplists(response)
    }

    private suspend fun fetchQqToplistSongCoverMap(topId: String): Map<String, String> {
        val topIdInt = topId.toIntOrNull() ?: return emptyMap()
        val response = api.postQqMusicu(body = buildQqToplistDetailRequestBody(topIdInt))
        return extractQqToplistSongCoverMap(response)
    }

    private fun fillMissingTrackCovers(
        tracks: List<Track>,
        coverMap: Map<String, String>
    ): List<Track> {
        if (tracks.isEmpty() || coverMap.isEmpty()) return tracks
        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = coverMap[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun fetchKuwoSongCovers(songIds: List<String>): Map<String, String> {
        if (songIds.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        songIds.chunked(KUWO_DETAIL_BATCH_SIZE).forEach { chunk ->
            val ridParam = chunk.joinToString(",") { "MUSIC_$it" }
            val responseBody = api.getKuwoSongMetaRaw(rid = ridParam)
            val raw = responseBody.use { it.string() }
            result.putAll(extractKuwoSongCoverMap(raw, json))
        }
        return result
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

    private companion object {
        const val NETEASE_DETAIL_BATCH_SIZE = 50
        const val NETEASE_COMMENT_SORT_RECOMMENDED = 1
        const val NETEASE_COMMENT_SORT_LATEST = 3
        const val QQ_DETAIL_BATCH_SIZE = 20
        const val KUWO_DETAIL_BATCH_SIZE = 50
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

private fun buildQqToplistDetailRequestBody(topId: Int): JsonElement = buildJsonObject {
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

private fun String.isLikelySameTitle(other: String): Boolean {
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

private fun String.normalizeComparisonText(): String {
    return lowercase()
        .replace(" ", "")
        .replace(Regex("""[\(\)（）\[\]【】《》\-—_·•.,，:：'"!?！？]"""), "")
}

private fun buildQqAlbumCoverUrl(albumMid: String): String {
    if (albumMid.isBlank()) return ""
    return "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
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

private fun shareRefererFor(url: String): String? {
    val normalizedUrl = url.lowercase()
    return when {
        "y.qq.com" in normalizedUrl -> "https://y.qq.com/"
        "music.163.com" in normalizedUrl || "163cn.tv" in normalizedUrl -> "https://music.163.com/"
        "kuwo.cn" in normalizedUrl -> "https://www.kuwo.cn/"
        else -> null
    }
}

private fun extractShareTarget(rawContent: String): String? {
    if (rawContent.isBlank()) return null

    val decoded = rawContent
        .replace("\\/", "/")
        .replace("&amp;", "&")

    val urlPatterns = listOf(
        Regex("""https?://(?:[A-Za-z0-9-]+\.)?y\.qq\.com[^\s"'<>\\]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://music\.163\.com[^\s"'<>\\]+""", RegexOption.IGNORE_CASE),
        Regex("""https?://(?:www\.)?kuwo\.cn[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
    )

    urlPatterns.forEach { pattern ->
        pattern.find(decoded)?.value?.trim()?.let { candidate ->
            if (candidate.isNotBlank()) return candidate
        }
    }

    return Regex("""(?:id|disstid|pid)\s*[:=]\s*["']?(\d{5,})""", RegexOption.IGNORE_CASE)
        .find(decoded)
        ?.groupValues
        ?.getOrNull(1)
}

private fun JsonObject.getIgnoreCase(key: String): JsonElement? {
    return this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

private fun JsonObject.firstStringOf(vararg keys: String): String? {
    keys.forEach { key ->
        val value = (getIgnoreCase(key) as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject.firstLongOf(vararg keys: String): Long? {
    keys.forEach { key ->
        val value = (getIgnoreCase(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        if (value != null) return value
    }
    return null
}
