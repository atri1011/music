package com.music.myapplication.domain.model

data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
