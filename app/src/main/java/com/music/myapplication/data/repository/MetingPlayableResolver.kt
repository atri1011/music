package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetingPlayableResolver @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val redirectClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun resolve(track: Track, quality: String): Result<String> = withContext(Dispatchers.IO) {
        val server = track.platform.toMetingServer()
            ?: return@withContext Result.Error(
                AppError.Api(message = "${track.platform.displayName}暂不支持 Meting 音源")
            )

        val songId = track.id.trim()
        if (songId.isBlank()) {
            return@withContext Result.Error(AppError.Parse(message = "Meting 缺少歌曲 ID"))
        }
        if (track.platform == Platform.QQ && songId.isDigitsOnly()) {
            return@withContext Result.Error(
                AppError.Parse(message = "Meting QQ 音源需要 songmid，当前歌曲仅有 songid")
            )
        }

        val requestUrl = METING_BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("server", server)
            .addQueryParameter("type", "url")
            .addQueryParameter("id", songId)
            .addQueryParameter("br", quality.toMetingBitrate())
            .build()

        val request = Request.Builder()
            .url(requestUrl)
            .head()
            .build()

        return@withContext try {
            redirectClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> Result.Error(
                        AppError.Api(message = "Meting 请求过于频繁，请 30 秒后重试", code = 429)
                    )

                    response.header("Location").isNullOrBlank().not() -> {
                        Result.Success(response.header("Location").orEmpty())
                    }

                    else -> Result.Error(
                        AppError.Api(
                            message = "Meting 未返回可播放链接（HTTP ${response.code}）",
                            code = response.code
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private fun Platform.toMetingServer(): String? = when (this) {
        Platform.NETEASE -> "netease"
        Platform.QQ -> "tencent"
        Platform.KUWO,
        Platform.LOCAL -> null
    }

    companion object {
        private const val METING_BASE_URL = "https://api.baka.plus/meting/"
    }
}

private fun String.toMetingBitrate(): String = when (lowercase()) {
    "320k" -> "320"
    "flac" -> "380"
    else -> "128"
}
