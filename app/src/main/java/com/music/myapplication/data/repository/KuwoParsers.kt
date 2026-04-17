package com.music.myapplication.data.repository

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal fun extractKuwoSongCoverMap(rawResponse: String, json: Json): Map<String, String> {
    if (rawResponse.isBlank()) return emptyMap()

    val normalized = rawResponse.replace('\'', '"')
    val root = runCatching { json.parseToJsonElement(normalized) }.getOrNull() as? JsonObject ?: return emptyMap()
    val songs = (root.getIgnoreCase("abslist") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songId = song.firstStringOf("id")
            ?.takeIf { it.isNotBlank() }
            ?: song.firstStringOf("MUSICRID", "musicrid")
                ?.removePrefix("MUSIC_")
                ?.takeIf { it.isNotBlank() }
            ?: return@forEach

        val rawCover = song.firstStringOf("web_albumpic_short", "web_albumpic_big", "picUrl", "pic").orEmpty()
        val coverUrl = normalizeKuwoAlbumCoverUrl(rawCover)
        if (coverUrl.isNotBlank()) {
            coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun normalizeKuwoAlbumCoverUrl(rawCover: String): String {
    if (rawCover.isBlank()) return ""
    if (rawCover.startsWith("http://", ignoreCase = true) || rawCover.startsWith("https://", ignoreCase = true)) {
        return rawCover
    }
    val cleaned = rawCover.trim().trimStart('/')
    if (cleaned.isBlank()) return ""
    return "https://img4.kuwo.cn/star/albumcover/$cleaned"
}

internal fun extractKuwoSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val root = data as? JsonObject ?: return emptyList()
    val container = root.getIgnoreCase("data") ?: root
    val items = when (type) {
        SearchType.ARTIST -> extractNestedArray(container, "artistList", "artistlist", "list")
        SearchType.ALBUM -> extractNestedArray(container, "albumList", "albumlist", "list")
        SearchType.PLAYLIST -> extractNestedArray(container, "playList", "playlist", "playlists", "list")
        SearchType.SONG -> null
    } ?: return emptyList()

    return items.mapNotNull { element ->
        when (type) {
            SearchType.ARTIST -> parseKuwoArtistSearchItem(element as? JsonObject)
            SearchType.ALBUM -> parseKuwoAlbumSearchItem(element as? JsonObject)
            SearchType.PLAYLIST -> parseKuwoPlaylistSearchItem(element as? JsonObject)
            SearchType.SONG -> null
        }
    }
}

private fun parseKuwoArtistSearchItem(item: JsonObject?): SearchResultItem? {
    val artist = item ?: return null
    val id = artist.firstStringOf("id", "artistid", "artistId") ?: return null
    val name = artist.firstStringOf("name", "artist", "artistName")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        coverUrl = normalizeQqImageUrl(artist.firstStringOf("pic", "htsPic", "pic300").orEmpty()),
        platform = Platform.KUWO,
        type = SearchType.ARTIST,
        trackCount = artist.firstLongOf("musicNum", "songNum", "songnum", "trackCount").toSearchCount()
    )
}

private fun parseKuwoAlbumSearchItem(item: JsonObject?): SearchResultItem? {
    val album = item ?: return null
    val id = album.firstStringOf("albumid", "albumId", "id") ?: return null
    val name = album.firstStringOf("album", "name", "albumName", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = album.firstStringOf("artist", "artistName", "singer", "artist_name").orEmpty()
            .ifBlank {
                extractJoinedNames(album.getIgnoreCase("artistList") ?: album.getIgnoreCase("artists"))
            },
        coverUrl = normalizeKuwoSearchCoverUrl(
            album.firstStringOf("pic", "img", "albumPic", "picUrl", "htsPic").orEmpty()
        ),
        platform = Platform.KUWO,
        type = SearchType.ALBUM,
        trackCount = album.firstLongOf("songnum", "songNum", "musicNum", "trackCount", "count").toSearchCount()
    )
}

private fun parseKuwoPlaylistSearchItem(item: JsonObject?): SearchResultItem? {
    val playlist = item ?: return null
    val id = playlist.firstStringOf("id", "playlistid", "pid") ?: return null
    val name = playlist.firstStringOf("name", "playlist", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = playlist.firstStringOf("uname", "nickName", "nickname").orEmpty(),
        coverUrl = normalizeKuwoSearchCoverUrl(
            playlist.firstStringOf("img", "pic", "imgurl", "coverUrl").orEmpty()
        ),
        platform = Platform.KUWO,
        type = SearchType.PLAYLIST,
        trackCount = playlist.firstLongOf("musicNum", "songNum", "songnum", "trackCount", "count").toSearchCount()
    )
}

private fun normalizeKuwoSearchCoverUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("//")
    ) {
        normalizeQqImageUrl(trimmed)
    } else {
        normalizeKuwoAlbumCoverUrl(trimmed)
    }
}
