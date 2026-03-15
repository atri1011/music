package com.music.myapplication.data.repository.online

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.buildQqAlbumInfoBody
import com.music.myapplication.data.repository.extractApiCode
import com.music.myapplication.data.repository.extractApiMessage
import com.music.myapplication.data.repository.extractQqAlbumId
import com.music.myapplication.data.repository.extractQqAlbumInfoData
import com.music.myapplication.data.repository.isDigitsOnly
import kotlinx.serialization.json.JsonObject

internal class OnlineMusicQqAlbumInfoFetcher(
    private val api: TuneHubApi
) {
    suspend fun fetchAlbumInfoData(idOrMid: String): Result<JsonObject> {
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

    suspend fun resolveAlbumId(idOrMid: String): String? {
        if (idOrMid.isBlank()) return null
        if (idOrMid.isDigitsOnly()) return idOrMid

        return runCatching {
            api.postQqMusicu(buildQqAlbumInfoBody(idOrMid))
        }.getOrNull()?.let(::extractQqAlbumId)
    }
}
