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

    @GET
    suspend fun loginCellphone(
        @Url url: String,
        @Query("phone") phone: String,
        @Query("password") password: String? = null,
        @Query("captcha") captcha: String? = null,
        @Query("countrycode") countryCode: String = "86",
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun sendCaptcha(
        @Url url: String,
        @Query("phone") phone: String,
        @Query("ctcode") countryCode: String = "86",
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun loginQrKey(
        @Url url: String,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun loginQrCreate(
        @Url url: String,
        @Query("key") key: String,
        @Query("qrimg") qrImage: Boolean = true,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun loginQrCheck(
        @Url url: String,
        @Query("key") key: String,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun loginStatus(
        @Url url: String,
        @Query("cookie") cookie: String,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun userPlaylist(
        @Url url: String,
        @Query("uid") uid: Long,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0,
        @Query("cookie") cookie: String,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun playlistTrackAll(
        @Url url: String,
        @Query("id") id: String,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0,
        @Query("cookie") cookie: String,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement

    @GET
    suspend fun likeList(
        @Url url: String,
        @Query("uid") uid: Long,
        @Query("cookie") cookie: String,
        @Query("realIP") realIp: String? = null,
        @Query("timestamp") timestamp: Long
    ): JsonElement
}
