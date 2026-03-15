package com.music.myapplication.core.network.retrofit

import com.music.myapplication.data.remote.dto.MethodsTemplateDto
import com.music.myapplication.data.remote.dto.AppUpdateManifestDto
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.data.remote.dto.ParseResponseDto
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface TuneHubApi {

    @GET
    suspend fun fetchAppUpdateManifest(
        @Url url: String
    ): AppUpdateManifestDto

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

    @GET("https://music.163.com/api/album/{id}")
    suspend fun getNeteaseAlbumDetail(
        @Path("id") id: String,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/v1/resource/comments/R_SO_4_{songId}")
    suspend fun getNeteaseSongComments(
        @Path("songId") songId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/v2/resource/comments/R_SO_4_{songId}")
    suspend fun getNeteaseSortedSongComments(
        @Path("songId") songId: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sortType") sortType: Int,
        @Query("cursor") cursor: String = "-1",
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

    @GET("https://music.163.com/api/mv/detail")
    suspend fun getNeteaseMvDetail(
        @Query("id") mvId: String,
        @Query("type") type: String = "mp4",
        @Header("Referer") referer: String = "https://music.163.com/"
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

    // Hot Search
    @GET("https://music.163.com/api/search/defaultkeyword/get")
    suspend fun getNeteaseDefaultKeyword(
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/search/hot/detail")
    suspend fun getNeteaseHotSearchDetail(
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/search/hot")
    suspend fun getNeteaseHotSearch(
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://c.y.qq.com/splcloud/fcgi-bin/gethotkey.fcg")
    suspend fun getQqHotSearch(
        @Query("format") format: String = "json",
        @Header("Referer") referer: String = "https://y.qq.com/"
    ): JsonElement

    // Search Suggestions
    @GET("https://music.163.com/api/search/suggest/web")
    suspend fun getNeteaseSearchSuggest(
        @Query("s") keyword: String,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg")
    suspend fun getQqSearchSuggest(
        @Query("key") keyword: String,
        @Query("format") format: String = "json",
        @Header("Referer") referer: String = "https://y.qq.com/"
    ): JsonElement

    @FormUrlEncoded
    @POST("https://music.163.com/api/search/get/web")
    suspend fun searchNeteaseByType(
        @Field("s") keyword: String,
        @Field("type") type: Int,
        @Field("offset") offset: Int = 0,
        @Field("limit") limit: Int = 20,
        @Field("total") total: Boolean = true,
        @Field("csrf_token") csrfToken: String = "",
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://www.kuwo.cn/api/www/search/searchArtistBykeyWord")
    suspend fun searchKuwoArtists(
        @Query("key") keyword: String,
        @Query("pn") page: Int = 1,
        @Query("rn") pageSize: Int = 20,
        @Query("httpsStatus") httpsStatus: Int = 1,
        @Header("Referer") referer: String = "https://www.kuwo.cn/",
        @Header("csrf") csrf: String = ""
    ): JsonElement

    @GET("https://www.kuwo.cn/api/www/search/searchAlbumBykeyWord")
    suspend fun searchKuwoAlbums(
        @Query("key") keyword: String,
        @Query("pn") page: Int = 1,
        @Query("rn") pageSize: Int = 20,
        @Query("httpsStatus") httpsStatus: Int = 1,
        @Header("Referer") referer: String = "https://www.kuwo.cn/",
        @Header("csrf") csrf: String = ""
    ): JsonElement

    @GET("https://www.kuwo.cn/api/www/search/searchPlayListBykeyWord")
    suspend fun searchKuwoPlaylists(
        @Query("key") keyword: String,
        @Query("pn") page: Int = 1,
        @Query("rn") pageSize: Int = 20,
        @Query("httpsStatus") httpsStatus: Int = 1,
        @Header("Referer") referer: String = "https://www.kuwo.cn/",
        @Header("csrf") csrf: String = ""
    ): JsonElement

    // Artist
    @GET("https://music.163.com/api/artist/{id}")
    suspend fun getNeteaseArtistDetail(
        @Path("id") artistId: String,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/artist/album/{id}")
    suspend fun getNeteaseArtistAlbums(
        @Path("id") artistId: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    // Playlist Browse
    @GET("https://music.163.com/api/playlist/catalogue")
    suspend fun getNeteasePlaylistCategories(
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://music.163.com/api/playlist/list")
    suspend fun getNeteasePlaylistByCategory(
        @Query("cat") category: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("order") order: String = "hot",
        @Header("Referer") referer: String = "https://music.163.com/"
    ): JsonElement

    @GET("https://c.y.qq.com/base/fcgi-bin/fcg_global_comment_h5.fcg")
    suspend fun getQqSongCommentsRaw(
        @Query("biztype") bizType: Int = 1,
        @Query("topid") songId: String,
        @Query("cmd") cmd: Int = 8,
        @Query("pagenum") pageNum: Int = 0,
        @Query("pagesize") pageSize: Int = 20,
        @Query("format") format: String = "json",
        @Header("Referer") referer: String = "https://y.qq.com/"
    ): ResponseBody
}
