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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicRepositoryImpl @Inject constructor(
    private val api: TuneHubApi,
    private val dispatchExecutor: DispatchExecutor
) : OnlineMusicRepository {

    override suspend fun search(
        platform: Platform, keyword: String, page: Int, pageSize: Int
    ): Result<List<Track>> {
        return dispatchExecutor.executeByMethod(
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
    }

    override suspend fun getToplists(platform: Platform): Result<List<ToplistInfo>> {
        return try {
            val result = dispatchExecutor.executeByMethod(
                platform = platform,
                function = "toplists",
                args = emptyMap()
            )
            when (result) {
                is Result.Success -> Result.Success(
                    result.data.map { track ->
                        ToplistInfo(
                            id = track.id,
                            name = track.title,
                            coverUrl = track.coverUrl
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
        return dispatchExecutor.executeByMethod(
            platform = platform,
            function = "toplist",
            args = mapOf("id" to id)
        )
    }

    override suspend fun getPlaylistDetail(platform: Platform, id: String): Result<List<Track>> {
        return dispatchExecutor.executeByMethod(
            platform = platform,
            function = "playlist",
            args = mapOf("id" to id)
        )
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
}
