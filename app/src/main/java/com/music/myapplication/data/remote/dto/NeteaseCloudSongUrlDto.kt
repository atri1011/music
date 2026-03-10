package com.music.myapplication.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NeteaseCloudSongUrlResponseDto(
    @SerialName("code") val code: Int = 0,
    @SerialName("data") val data: List<NeteaseCloudSongUrlItemDto> = emptyList()
)

@Serializable
data class NeteaseCloudSongUrlItemDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("br") val br: Int? = null,
    @SerialName("code") val code: Int = 0,
    @SerialName("level") val level: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("freeTrialInfo") val freeTrialInfo: JsonElement? = null
)
