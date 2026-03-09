package com.music.myapplication.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JkApiResponseDto(
    val code: Int = 0,
    val msg: String = "",
    val name: String = "",
    val album: String = "",
    val artist: String = "",
    @SerialName("music_url") val musicUrl: String = ""
)
