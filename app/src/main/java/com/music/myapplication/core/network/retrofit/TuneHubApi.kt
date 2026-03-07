package com.music.myapplication.core.network.retrofit

import com.music.myapplication.data.remote.dto.MethodsTemplateDto
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.data.remote.dto.ParseResponseDto
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TuneHubApi {

    @POST("v1/parse")
    suspend fun parse(
        @Header("X-API-Key") apiKey: String,
        @Body request: ParseRequestDto
    ): ParseResponseDto

    @GET("v1/methods/{platform}/{function}")
    suspend fun getMethodTemplate(
        @Path("platform") platform: String,
        @Path("function") function: String
    ): MethodsTemplateDto

    @GET("https://music.163.com/api/song/detail/")
    suspend fun getNeteaseSongDetail(
        @Query("ids") ids: String,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/personalized/playlist")
    suspend fun getNeteaseRecommendedPlaylists(
        @Query("limit") limit: Int,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/v6/playlist/detail")
    suspend fun getNeteasePlaylistDetailV6(
        @Query("id") id: String,
        @Query("n") n: Int = 100000,
        @Query("s") s: Int = 8,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg")
    suspend fun getQqSongDetail(
        @Query("songmid") songMid: String,
        @Query("tpl") tpl: String = "yqq_song_detail",
        @Query("format") format: String = "json",
        @Query("platform") platform: String = "yqq.json",
        @Query("needNewCode") needNewCode: Int = 0,
        @Header("Referer") referer: String = "https://y.qq.com/"
    ): JsonElement

    @GET("http://search.kuwo.cn/r.s")
    suspend fun getKuwoSongMetaRaw(
        @Query("rid") rid: String,
        @Query("client") client: String = "kt",
        @Query("rformat") responseFormat: String = "json",
        @Query("rn") rn: Int = 200,
        @Query("pn") pn: Int = 0
    ): ResponseBody

    @POST("https://u.y.qq.com/cgi-bin/musicu.fcg")
    suspend fun postQqMusicu(
        @Body body: JsonElement,
        @Header("Referer") referer: String = "https://y.qq.com/"
    ): JsonElement
}
