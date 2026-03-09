package com.music.myapplication.app.navigation

import kotlinx.serialization.Serializable

sealed interface Routes {
    @Serializable data object Home : Routes
    @Serializable data object Search : Routes
    @Serializable data object Library : Routes
    @Serializable data object More : Routes
    @Serializable data object PlayerLyrics : Routes
    @Serializable data object Downloaded : Routes
    @Serializable data object LocalMusic : Routes
    @Serializable data object Equalizer : Routes
    @Serializable data class PlaylistDetail(
        val id: String,
        val platform: String,
        val name: String = "",
        val source: String = "toplist"
    ) : Routes

    @Serializable data class ArtistDetail(
        val artistId: String,
        val platform: String,
        val artistName: String = ""
    ) : Routes

    @Serializable data class AlbumDetail(
        val albumId: String,
        val platform: String,
        val albumName: String = "",
        val artistName: String = "",
        val coverUrl: String = ""
    ) : Routes
}
