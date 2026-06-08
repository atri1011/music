package com.music.myapplication.data.repository.online

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.data.repository.buildQqMvRequestBody
import com.music.myapplication.data.repository.extractNeteaseMvId
import com.music.myapplication.data.repository.extractNeteaseMvUrl
import com.music.myapplication.data.repository.extractQqMvUrl
import com.music.myapplication.data.repository.extractQqMvVid
import com.music.myapplication.data.repository.extractShareTarget
import com.music.myapplication.data.repository.isDigitsOnly
import com.music.myapplication.data.repository.shareRefererFor
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.util.Base64

internal class OnlineMusicMediaResolver(
    private val api: TuneHubApi,
    private val okHttpClient: OkHttpClient,
    private val resolveQqTrackCandidate: suspend (Track) -> Track?
) {
    suspend fun resolveShareUrl(url: String): String = withContext(Dispatchers.IO) {
        val normalizedInput = url.trim()
        if (normalizedInput.isBlank()) return@withContext normalizedInput

        val requestUrl = ShareUtils.extractShareUrlCandidates(normalizedInput)
            .firstOrNull { candidate ->
                candidate.startsWith("http://", ignoreCase = true) ||
                    candidate.startsWith("https://", ignoreCase = true)
            }
            ?: return@withContext normalizedInput

        runCatching {
            val requestBuilder = Request.Builder().url(requestUrl).get()
            shareRefererFor(requestUrl)?.let { referer ->
                requestBuilder.header("Referer", referer)
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val finalUrl = ShareUtils.normalizeShareUrlCandidate(response.request.url.toString())
                val responseBody = response.body?.string().orEmpty()
                when {
                    finalUrl.isNotBlank() && !finalUrl.equals(requestUrl, ignoreCase = true) -> finalUrl
                    else -> extractShareTarget(responseBody) ?: finalUrl.ifBlank { requestUrl }
                }
            }
        }.getOrDefault(extractShareTarget(normalizedInput) ?: requestUrl)
    }

    suspend fun resolvePlayableUrl(
        platform: Platform,
        songId: String,
        quality: String
    ): Result<String> {
        return try {
            val response = api.parse(
                apiKey = "",
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

    suspend fun resolveVideoUrl(track: Track): Result<String> {
        if (track.platform == Platform.LOCAL) {
            return Result.Error(AppError.Api(message = "本地歌曲暂不支持 MV 播放"))
        }

        return try {
            resolveVideoUrlFromParse(track)?.let { return Result.Success(it) }

            when (track.platform) {
                Platform.NETEASE -> resolveNeteaseVideoUrl(track)
                Platform.QQ -> resolveQqVideoUrl(track)
                Platform.KUWO -> Result.Error(AppError.Api(message = "酷我音乐暂未接入 MV 解析"))
                Platform.LOCAL -> Result.Error(AppError.Api(message = "本地歌曲暂不支持 MV 播放"))
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    suspend fun getLyrics(platform: Platform, songId: String): Result<LyricsResult> {
        val tuneHubResult = getTuneHubLyrics(platform, songId)
        if (tuneHubResult is Result.Success && tuneHubResult.data.lyric.isNotBlank()) {
            return tuneHubResult
        }

        val officialResult = getOfficialLyrics(platform, songId)
        if (officialResult is Result.Success && officialResult.data.lyric.isNotBlank()) {
            val tuneHubTranslation = (tuneHubResult as? Result.Success)?.data?.translation
            return Result.Success(
                officialResult.data.copy(
                    translation = officialResult.data.translation?.ifBlank { null } ?: tuneHubTranslation
                )
            )
        }

        return tuneHubResult
    }

    private suspend fun getTuneHubLyrics(platform: Platform, songId: String): Result<LyricsResult> {
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
                val translation = extractTranslation(response.data)
                Result.Success(LyricsResult(lyric = lyric.orEmpty(), translation = translation))
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun getOfficialLyrics(platform: Platform, songId: String): Result<LyricsResult>? {
        val normalizedSongId = songId.trim()
        if (normalizedSongId.isBlank()) return null

        return when (platform) {
            Platform.NETEASE -> getOfficialNeteaseLyrics(normalizedSongId)
            Platform.QQ -> getOfficialQqLyrics(normalizedSongId)
            Platform.KUWO, Platform.LOCAL -> null
        }
    }

    private suspend fun getOfficialNeteaseLyrics(songId: String): Result<LyricsResult>? {
        if (!songId.isDigitsOnly()) return null

        return runCatching {
            val response = api.getNeteaseLyrics(id = songId)
            Result.Success(
                LyricsResult(
                    lyric = extractNestedText(response, listOf("lrc"), listOf("lyric")).orEmpty(),
                    translation = extractNestedText(response, listOf("tlyric"), listOf("lyric"))
                )
            )
        }.getOrElse { e ->
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun getOfficialQqLyrics(songId: String): Result<LyricsResult>? {
        val plainLyrics = if (!songId.isDigitsOnly()) {
            runCatching {
                val response = api.getQqLyrics(songMid = songId)
                LyricsResult(
                    lyric = extractLyric(response).orEmpty(),
                    translation = extractTranslation(response)
                )
            }.getOrNull()
        } else {
            null
        }
        if (!plainLyrics?.lyric.isNullOrBlank()) {
            return Result.Success(plainLyrics)
        }

        return runCatching {
            val response = api.postQqMusicu(body = buildQqLyricsRequestBody(songId))
            Result.Success(
                LyricsResult(
                    lyric = extractQqMusicuLyric(response, "lyric").orEmpty(),
                    translation = extractQqMusicuLyric(response, "trans")
                )
            )
        }.getOrElse { e ->
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun resolveVideoUrlFromParse(track: Track): String? {
        val requests = listOf(
            ParseRequestDto(
                platform = track.platform.id,
                ids = track.id,
                quality = "mv"
            ),
            ParseRequestDto(platform = track.platform.id, ids = track.id)
        )

        requests.forEach { request ->
            val response = runCatching {
                api.parse(apiKey = "", request = request)
            }.getOrNull() ?: return@forEach

            if (response.code != 0) return@forEach
            extractVideoUrl(response.data)?.let { return it }
        }

        return null
    }

    private suspend fun resolveNeteaseVideoUrl(track: Track): Result<String> {
        if (!track.id.isDigitsOnly()) {
            return Result.Error(AppError.Parse(message = "网易云歌曲 ID 无效，无法解析 MV"))
        }

        val songResponse = api.getNeteaseSongDetail(ids = "[${track.id}]")
        val mvId = extractNeteaseMvId(songResponse)
            ?: return Result.Error(AppError.Api(message = "当前歌曲没有可播放的 MV"))
        val mvResponse = api.getNeteaseMvDetail(mvId = mvId)
        val playUrl = extractNeteaseMvUrl(mvResponse) ?: extractVideoUrl(mvResponse)
        return if (playUrl.isNullOrBlank()) {
            Result.Error(AppError.Parse(message = "解析网易云 MV 地址失败"))
        } else {
            Result.Success(playUrl)
        }
    }

    private suspend fun resolveQqVideoUrl(track: Track): Result<String> {
        val targetTrack = if (track.id.isDigitsOnly()) {
            resolveQqTrackCandidate(track)
                ?.takeIf { !it.id.isDigitsOnly() }
                ?: track
        } else {
            track
        }

        if (targetTrack.id.isDigitsOnly()) {
            return Result.Error(AppError.Parse(message = "QQ 音乐缺少 songmid，无法解析 MV"))
        }

        val songResponse = api.getQqSongDetail(songMid = targetTrack.id)
        val vid = extractQqMvVid(songResponse)
            ?: return Result.Error(AppError.Api(message = "当前歌曲没有可播放的 MV"))
        val mvResponse = api.postQqMusicu(body = buildQqMvRequestBody(vid))
        val playUrl = extractQqMvUrl(mvResponse, vid) ?: extractVideoUrl(mvResponse)
        return if (playUrl.isNullOrBlank()) {
            Result.Error(AppError.Parse(message = "解析 QQ 音乐 MV 地址失败"))
        } else {
            Result.Success(playUrl)
        }
    }

    private fun extractPlayableUrl(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("url", "playUrl", "play_url", "musicUrl"))
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let(::normalizePlayableUrl)
    }

    private fun extractVideoUrl(data: JsonElement?): String? {
        return findFirstMatch(
            data,
            listOf(
                "mvUrl",
                "mv_url",
                "mvPlayUrl",
                "mv_play_url",
                "videoUrl",
                "video_url",
                "videoPlayUrl",
                "video_play_url",
                "mp4Url",
                "mp4_url",
                "hlsUrl",
                "hls_url"
            )
        )?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let(::normalizePlayableUrl)
            ?: findFirstVideoLikeUrl(data)?.let(::normalizePlayableUrl)
    }

    private fun extractLyric(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("lyric", "lrc", "lyrics", "lyricText"))
    }

    private fun extractTranslation(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("tlyric", "trans", "translation", "translateLyric", "transLyric"))
    }

    private fun extractNestedText(data: JsonElement?, path: List<String>, keys: List<String>): String? {
        val target = path.fold(data) { current, key ->
            (current as? JsonObject)?.let { obj ->
                obj[key] ?: obj.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            }
        }
        return findFirstMatch(target, keys)
    }

    private fun extractQqMusicuLyric(data: JsonElement?, key: String): String? {
        val encoded = extractNestedText(data, listOf("req", "data"), listOf(key))
            ?.takeIf(String::isNotBlank)
            ?: return null

        return runCatching {
            String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun buildQqLyricsRequestBody(songId: String): JsonElement = buildJsonObject {
        put("comm", buildJsonObject {
            put("ct", 24)
            put("cv", 0)
            put("format", "json")
            put("inCharset", "utf-8")
            put("outCharset", "utf-8")
        })
        put("req", buildJsonObject {
            put("module", "music.musichallSong.PlayLyricInfo")
            put("method", "GetPlayLyricInfo")
            put("param", buildJsonObject {
                if (songId.isDigitsOnly()) {
                    put("songID", songId.toLongOrNull() ?: 0L)
                    put("songMID", "")
                } else {
                    put("songID", 0)
                    put("songMID", songId)
                }
            })
        })
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

    private fun findFirstVideoLikeUrl(element: JsonElement?): String? {
        return when (element) {
            is JsonObject -> {
                element.entries.firstNotNullOfOrNull { (key, value) ->
                    when (value) {
                        is JsonPrimitive -> {
                            value.contentOrNull
                                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                                ?.takeIf { isVideoKey(key) || isLikelyVideoUrl(it) }
                        }

                        else -> findFirstVideoLikeUrl(value)
                    }
                }
            }

            is JsonArray -> element.firstNotNullOfOrNull(::findFirstVideoLikeUrl)
            is JsonPrimitive -> {
                element.contentOrNull
                    ?.takeIf { it.startsWith("http", ignoreCase = true) && isLikelyVideoUrl(it) }
            }

            else -> null
        }
    }

    private fun isVideoKey(key: String): Boolean {
        val normalized = key.lowercase()
        return normalized.contains("mv") ||
            normalized.contains("video") ||
            normalized.contains("mp4") ||
            normalized.contains("m3u8")
    }

    private fun isLikelyVideoUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains(".mp4") ||
            normalized.contains(".m3u8") ||
            normalized.contains(".webm") ||
            normalized.contains("mime=video") ||
            normalized.contains("type=mp4") ||
            normalized.contains("format=mp4")
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
}
