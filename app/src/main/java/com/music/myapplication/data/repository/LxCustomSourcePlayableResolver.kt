package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.data.repository.lx.LxCustomScriptRepository
import com.music.myapplication.data.repository.lx.LxCustomScriptRuntime
import com.music.myapplication.data.repository.lx.LxKnownSource
import com.music.myapplication.data.repository.lx.LxMusicRequestPayload
import com.music.myapplication.data.repository.lx.toLxMusicInfo
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxCustomSourcePlayableResolver @Inject constructor(
    private val scriptRepository: LxCustomScriptRepository,
    private val runtime: LxCustomScriptRuntime,
    private val onlineRepo: OnlineMusicRepository
) {
    suspend fun resolve(track: Track, quality: String): Result<String> {
        if (track.platform == Platform.LOCAL) {
            return Result.Error(AppError.Parse(message = "本地歌曲无需落雪脚本解析"))
        }

        val sourceId = when (track.platform) {
            Platform.NETEASE -> LxKnownSource.NETEASE.id
            Platform.QQ -> LxKnownSource.QQ.id
            Platform.KUWO -> LxKnownSource.KUWO.id
            Platform.LOCAL -> null
        } ?: return Result.Error(AppError.Parse(message = "当前平台不支持落雪脚本解析"))

        val script = scriptRepository.getActiveValidatedScript()
            ?: return Result.Error(AppError.Parse(message = "未配置可用的 LX Music 自定义源脚本"))

        val qqSongMidCandidate = if (track.platform == Platform.QQ && track.id.isDigitsOnly()) {
            findQqSongMidCandidate(track)
        } else {
            null
        }

        return runtime.resolveMusicUrl(
            script = script,
            sourceId = sourceId,
            payload = LxMusicRequestPayload(
                type = quality,
                musicInfo = track.toLxMusicInfo(
                    quality = quality,
                    sourceId = sourceId,
                    qqSongMidCandidate = qqSongMidCandidate
                )
            )
        )
    }

    private suspend fun findQqSongMidCandidate(track: Track): String? {
        val keyword = listOf(track.title, track.artist)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val searchResult = onlineRepo.search(
            platform = Platform.QQ,
            keyword = keyword,
            page = 1,
            pageSize = 20
        )
        val candidates = (searchResult as? Result.Success)?.data.orEmpty()
            .filter { candidate -> candidate.id.isNotBlank() && !candidate.id.isDigitsOnly() }
        if (candidates.isEmpty()) return null

        return candidates.firstOrNull { candidate ->
            candidate.title.isLikelySameTitle(track.title) &&
                candidate.artist.isLikelySameArtist(track.artist)
        }?.id ?: candidates.firstOrNull { candidate ->
            candidate.title.isLikelySameTitle(track.title)
        }?.id ?: candidates.firstOrNull()?.id
    }
}
