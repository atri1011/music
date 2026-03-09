package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.JkApi
import com.music.myapplication.data.remote.dto.JkApiResponseDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JkApiPlayableResolver @Inject constructor(
    private val api: JkApi,
    private val preferences: PlayerPreferences
) {
    suspend fun resolve(track: Track): Result<String> {
        val plat = when (track.platform) {
            Platform.NETEASE -> "wy"
            Platform.QQ -> "qq"
            Platform.KUWO -> return Result.Error(AppError.Api(message = "JKAPI 不支持酷我音乐"))
        }

        val apiKey = preferences.currentJkapiKey
        if (apiKey.isBlank()) {
            return Result.Error(AppError.Api(message = "请先设置 JKAPI 密钥"))
        }

        val keyword = buildKeyword(track)
        if (keyword.isBlank()) {
            return Result.Error(AppError.Parse(message = "无法构建搜索关键词"))
        }

        return try {
            val response = api.searchMusic(plat = plat, apiKey = apiKey, name = keyword)
            when {
                response.code != 1 -> Result.Error(
                    AppError.Api(message = response.msg.ifBlank { "JKAPI 解析失败" })
                )
                response.musicUrl.isBlank() -> Result.Error(
                    AppError.Parse(message = "JKAPI 未返回播放地址")
                )
                !isReasonableMatch(track, response) -> Result.Error(
                    AppError.Parse(message = "JKAPI 匹配结果不一致")
                )
                else -> Result.Success(response.musicUrl)
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private fun buildKeyword(track: Track): String =
        listOf(track.title, track.artist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun isReasonableMatch(track: Track, response: JkApiResponseDto): Boolean {
        if (response.name.isBlank()) return true
        val titleMatch = response.name.contains(track.title, ignoreCase = true) ||
            track.title.contains(response.name, ignoreCase = true)
        return titleMatch
    }
}
