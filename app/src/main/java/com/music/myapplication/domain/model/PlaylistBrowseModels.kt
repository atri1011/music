package com.music.myapplication.domain.model

data class PlaylistCategory(
    val name: String,
    val hot: Boolean = false
)

data class PlaylistPreview(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val playCount: Long = 0,
    val description: String = "",
    val platform: Platform = Platform.NETEASE
)
