package com.music.myapplication.domain.model

enum class SearchType(val displayName: String, val dispatchFunction: String) {
    SONG("单曲", "search"),
    ARTIST("歌手", "searchArtist"),
    ALBUM("专辑", "searchAlbum"),
    PLAYLIST("歌单", "searchPlaylist")
}

data class SearchResultItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val platform: Platform,
    val type: SearchType,
    val trackCount: Int = 0
)
