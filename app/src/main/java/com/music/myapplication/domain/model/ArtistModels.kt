package com.music.myapplication.domain.model

data class ArtistRef(
    val id: String,
    val name: String,
    val platform: Platform
)

data class ArtistDetail(
    val id: String,
    val name: String,
    val platform: Platform,
    val avatarUrl: String = "",
    val hotSongs: List<Track> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList()
)

data class ArtistAlbum(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val publishTime: String = "",
    val songCount: Int = 0
)
