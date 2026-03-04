package com.music.myapplication.core.network.retrofit

import com.music.myapplication.data.remote.dto.MethodsTemplateDto
import com.music.myapplication.data.remote.dto.ParseRequestDto
import com.music.myapplication.data.remote.dto.ParseResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

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
}
