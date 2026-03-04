package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.dispatch.DispatchExecutor
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicRepositoryImpl @Inject constructor(
    private val api: TuneHubApi,
    private val dispatchExecutor: DispatchExecutor,
    private val json: Json
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

    override suspend fun getToplistDetail(platform: Platform, id: String): Result<List<Track>> {
        val result = dispatchExecutor.executeByMethod(
            platform = platform,
            function = "toplist",
            args = mapOf("id" to id)
        )
        val enrichedResult = enrichTrackCoverIfNeeded(platform, result)
        if (platform != Platform.QQ || enrichedResult !is Result.Success) return enrichedResult

        val tracks = enrichedResult.data
        if (tracks.none { it.coverUrl.isBlank() }) return enrichedResult

        val coverMap = runCatching {
            fetchQqToplistSongCoverMap(id)
        }.getOrDefault(emptyMap())

        if (coverMap.isEmpty()) return enrichedResult
        return Result.Success(fillMissingTrackCovers(tracks, coverMap))
    }

    override suspend fun getPlaylistDetail(platform: Platform, id: String): Result<List<Track>> {
        val result = dispatchExecutor.executeByMethod(
            platform = platform,
            function = "playlist",
            args = mapOf("id" to id)
        )
        return enrichTrackCoverIfNeeded(platform, result)
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

    override suspend fun getLyrics(platform: Platform, songId: String): Result<String> {
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
                else Result.Success(lyric)
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private fun extractPlayableUrl(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("url", "playUrl", "play_url", "musicUrl"))
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let(::normalizePlayableUrl)
    }

    private fun extractLyric(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("lyric", "lrc", "lyrics", "lyricText"))
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

    private fun String.isDigitsOnly(): Boolean = isNotEmpty() && all(Char::isDigit)

    private companion object {
        const val NETEASE_DETAIL_BATCH_SIZE = 50
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
