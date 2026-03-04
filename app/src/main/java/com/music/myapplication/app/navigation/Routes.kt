package com.music.myapplication.app.navigation

import kotlinx.serialization.Serializable

sealed interface Routes {
    @Serializable data object Home : Routes
    @Serializable data object Search : Routes
    @Serializable data object Library : Routes
    @Serializable data object PlayerLyrics : Routes
    @Serializable data class PlaylistDetail(val id: String, val platform: String, val name: String = "") : Routes
}
