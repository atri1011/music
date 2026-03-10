package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeteaseCloudApiPlayableResolver @Inject constructor(
    private val api: NeteaseCloudApiEnhancedApi,
    private val preferences: PlayerPreferences
) {
    suspend fun resolve(track: Track, quality: String): Result<String> {
        if (track.platform != Platform.NETEASE) {
            return Result.Error(AppError.Api(message = "网易云增强版接口仅支持网易云歌曲"))
        }

        val baseUrl = preferences.neteaseCloudApiBaseUrl.first()
        if (baseUrl.isBlank()) {
            return Result.Error(AppError.Api(message = "请先设置网易云增强版接口地址"))
        }

        return try {
            val response = api.songUrlV1(
                url = buildSongUrlEndpoint(baseUrl),
                id = track.id,
                level = quality.toNeteaseSongLevel()
            )
            if (response.code != 200) {
                Result.Error(AppError.Api(message = "网易云增强版接口请求失败", code = response.code))
            } else {
                val item = response.data.firstOrNull()
                    ?: return Result.Error(AppError.Parse(message = "网易云增强版接口未返回歌曲数据"))
                val playableUrl = item.url
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?.let(::normalizePlayableUrl)
                if (playableUrl.isNullOrBlank()) {
                    val message = when {
                        item.code != 200 -> "网易云增强版接口未返回可播放链接（code=${item.code}）"
                        item.freeTrialInfo != null -> "当前歌曲仅返回试听片段，建议提供登录态或切换其他音源"
                        else -> "网易云增强版接口未返回可播放链接"
                    }
                    Result.Error(AppError.Parse(message = message))
                } else {
                    Result.Success(playableUrl)
                }
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private fun buildSongUrlEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/song/url/v1", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/song/url/v1"
        }
    }

    private fun normalizePlayableUrl(rawUrl: String): String {
        val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        return if (parsed.scheme.equals("http", ignoreCase = true)) {
            parsed.newBuilder().scheme("https").build().toString()
        } else {
            rawUrl
        }
    }
}

private fun String.toNeteaseSongLevel(): String = when (lowercase()) {
    "128k" -> "standard"
    "320k" -> "exhigh"
    "flac" -> "lossless"
    else -> "standard"
}
