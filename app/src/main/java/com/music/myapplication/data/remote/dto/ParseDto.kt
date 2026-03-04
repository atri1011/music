package com.music.myapplication.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ParseRequestDto(
    @SerialName("platform") val platform: String,
    @SerialName("ids") val ids: String,
    @EncodeDefault
    @SerialName("quality") val quality: String = "128k",
    @SerialName("id") val id: String = ids
)

@Serializable
data class ParseResponseDto(
    @SerialName("code") val code: Int = 0,
    @SerialName("msg") val msg: String = "",
    @SerialName("message") val message: String = "",
    @SerialName("data") val data: JsonElement? = null
) {
    val resolvedMessage: String
        get() = message.ifBlank { msg }
}
