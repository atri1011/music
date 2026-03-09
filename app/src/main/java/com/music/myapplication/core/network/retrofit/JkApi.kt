package com.music.myapplication.core.network.retrofit

import com.music.myapplication.data.remote.dto.JkApiResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface JkApi {

    @GET("api/music")
    suspend fun searchMusic(
        @Query("plat") plat: String,
        @Query("type") type: String = "json",
        @Query("apiKey") apiKey: String,
        @Query("name") name: String
    ): JkApiResponseDto
}
