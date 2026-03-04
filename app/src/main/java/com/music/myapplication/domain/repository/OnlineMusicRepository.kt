package com.music.myapplication.domain.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track

interface OnlineMusicRepository {
    suspend fun search(platform: Platform, keyword: String, page: Int = 1, pageSize: Int = 20): Result<List<Track>>
    suspend fun getToplists(platform: Platform): Result<List<ToplistInfo>>
    suspend fun getToplistDetail(platform: Platform, id: String): Result<List<Track>>
    suspend fun getPlaylistDetail(platform: Platform, id: String): Result<List<Track>>
    suspend fun resolvePlayableUrl(platform: Platform, songId: String, quality: String = "128k"): Result<String>
    suspend fun getLyrics(platform: Platform, songId: String): Result<String>
}

data class ToplistInfo(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val description: String = ""
)
