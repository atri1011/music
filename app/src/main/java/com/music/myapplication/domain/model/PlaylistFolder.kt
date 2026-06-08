package com.music.myapplication.domain.model

data class PlaylistFolder(
    val id: String,
    val name: String,
    val playlistCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
