package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TuneHubPlayableResolver @Inject constructor(
    private val api: TuneHubApi
) {
    suspend fun resolve(track: Track, quality: String): Result<String> {
        return try {
            val response = api.parse(
                apiKey = "",
                request = ParseRequestDto(
                    platform = track.platform.id,
                    ids = track.id,
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

    private fun extractPlayableUrl(data: JsonElement?): String? {
        return findFirstMatch(data, listOf("url", "playUrl", "play_url", "musicUrl"))
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let(::normalizePlayableUrl)
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
        val shouldUpgrade = host == "wx.music.tc.qq.com" ||
            host.endsWith(".music.tc.qq.com") ||
            host.endsWith(".qqmusic.qq.com")
        return if (shouldUpgrade) {
            parsed.newBuilder().scheme("https").build().toString()
        } else {
            rawUrl
        }
    }
}
