package com.music.myapplication.domain.model

data class AlbumInfo(
    val id: String = "",
    val name: String = "",
    val artistName: String = "",
    val coverUrl: String = "",
    val publishTime: String = "",
    val description: String = "",
    val company: String = "",
    val genre: String = "",
    val language: String = "",
    val subType: String = "",
    val tags: List<String> = emptyList(),
    val trackCount: Int = 0,
    val platform: Platform = Platform.NETEASE
)

data class AlbumDetailResult(
    val info: AlbumInfo = AlbumInfo(),
    val tracks: List<Track> = emptyList()
)
