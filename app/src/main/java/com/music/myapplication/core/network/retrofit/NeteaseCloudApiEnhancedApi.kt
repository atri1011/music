package com.music.myapplication.core.network.retrofit

import com.music.myapplication.data.remote.dto.NeteaseCloudSongUrlResponseDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface NeteaseCloudApiEnhancedApi {

    @GET
    suspend fun songUrlV1(
        @Url url: String,
        @Query("id") id: String,
        @Query("level") level: String
    ): NeteaseCloudSongUrlResponseDto

    @GET
    suspend fun album(
        @Url url: String,
        @Query("id") id: String
    ): JsonElement

    @GET
    suspend fun artistDetail(
        @Url url: String,
        @Query("id") id: String
    ): JsonElement

    @GET
    suspend fun artistDesc(
        @Url url: String,
        @Query("id") id: String
    ): JsonElement
}
