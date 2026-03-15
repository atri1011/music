package com.music.myapplication.data.repository.online

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.ExtractedCommentPage
import com.music.myapplication.data.repository.extractApiCode
import com.music.myapplication.data.repository.extractApiMessage
import com.music.myapplication.data.repository.extractNeteaseSortedTrackComments
import com.music.myapplication.data.repository.extractNeteaseTrackComments
import com.music.myapplication.data.repository.extractQqTrackComments
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.repository.TrackCommentsResult
import kotlinx.serialization.json.Json

internal class OnlineMusicCommentsFetcher(
    private val api: TuneHubApi,
    private val json: Json
) {
    suspend fun fetchNeteaseTrackComments(
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

    suspend fun fetchQqTrackComments(
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

    private companion object {
        const val NETEASE_COMMENT_SORT_RECOMMENDED = 1
        const val NETEASE_COMMENT_SORT_LATEST = 3
    }
}
