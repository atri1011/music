package com.music.myapplication.data.repository

import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.domain.model.ArtistAlbum
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun extractNeteasePlaylistTracks(data: JsonElement?): List<Track> {
    val root = data as? JsonObject ?: return emptyList()
    val playlist = (root.getIgnoreCase("playlist") ?: root.getIgnoreCase("result")) as? JsonObject
        ?: return emptyList()
    val tracks = playlist.getIgnoreCase("tracks") as? JsonArray ?: return emptyList()

    return tracks.mapNotNull { itemElement ->
        extractNeteaseTrack(itemElement as? JsonObject)
    }
}

internal fun extractNeteasePlaylistTrackIds(data: JsonElement?): List<String> {
    val root = data as? JsonObject ?: return emptyList()
    val playlist = (root.getIgnoreCase("playlist") ?: root.getIgnoreCase("result")) as? JsonObject
        ?: return emptyList()
    val trackIds = playlist.getIgnoreCase("trackIds") as? JsonArray ?: return emptyList()

    return trackIds.mapNotNull { itemElement ->
        (itemElement as? JsonObject)
            ?.firstStringOf("id")
            ?.takeIf { it.isNotBlank() }
    }.distinct()
}

internal fun extractNeteaseSongTracks(data: JsonElement?): List<Track> {
    val songs = ((data as? JsonObject)?.getIgnoreCase("songs") as? JsonArray) ?: return emptyList()
    return songs.mapNotNull { itemElement ->
        extractNeteaseTrack(itemElement as? JsonObject)
    }
}

internal fun extractNeteaseSongCoverMap(data: JsonElement?): Map<String, String> {
    val songs = ((data as? JsonObject)?.getIgnoreCase("songs") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songId = song.firstStringOf("id").orEmpty()
        if (songId.isBlank()) return@forEach

        val album = (song.getIgnoreCase("album") ?: song.getIgnoreCase("al")) as? JsonObject
        val coverUrl = album?.firstStringOf("picUrl", "blurPicUrl").orEmpty()
        if (coverUrl.isNotBlank()) {
            coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun extractNeteaseMvId(data: JsonElement?): String? {
    val song = ((data as? JsonObject)?.getIgnoreCase("songs") as? JsonArray)
        ?.firstOrNull() as? JsonObject ?: return null
    val mvValue = song.getIgnoreCase("mv")
    val mvObject = mvValue as? JsonObject
    return mvObject?.firstStringOf("id", "mvId", "mvid")
        ?.takeIf { it.isNotBlank() && it != "0" }
        ?: song.firstStringOf("mv", "mvid")
            ?.takeIf { it.isNotBlank() && it != "0" }
}

internal fun extractNeteaseMvUrl(data: JsonElement?): String? {
    val root = data as? JsonObject ?: return null
    val dataNode = (root.getIgnoreCase("data") as? JsonObject) ?: root
    val brs = dataNode.getIgnoreCase("brs") as? JsonObject
    listOf("1080", "720", "480", "240").forEach { resolution ->
        val url = (brs?.getIgnoreCase(resolution) as? JsonPrimitive)?.contentOrNull
        if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
    }

    return dataNode.firstStringOf("url", "playUrl", "mp4Url", "hlsUrl")
        ?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?.let(::normalizeVideoLikeUrl)
        ?: extractFirstVideoLikeUrl(dataNode)?.let(::normalizeVideoLikeUrl)
}

private fun extractNeteaseArtistText(track: JsonObject): String {
    val artists = (track.getIgnoreCase("ar") ?: track.getIgnoreCase("artists")) as? JsonArray ?: return ""
    return artists.mapNotNull { artistElement ->
        (artistElement as? JsonObject)?.firstStringOf("name")
    }.joinToString("/")
}

internal fun extractNeteaseTrack(track: JsonObject?): Track? {
    val item = track ?: return null
    val id = item.firstStringOf("id").orEmpty()
    if (id.isBlank()) return null

    val album = (item.getIgnoreCase("al") ?: item.getIgnoreCase("album")) as? JsonObject
    return Track(
        id = id,
        platform = Platform.NETEASE,
        title = item.firstStringOf("name", "title").orEmpty(),
        artist = extractNeteaseArtistText(item),
        album = album?.firstStringOf("name").orEmpty(),
        albumId = album?.firstStringOf("id", "albumId", "albumID").orEmpty(),
        coverUrl = album?.firstStringOf("picUrl", "blurPicUrl").orEmpty(),
        durationMs = item.firstLongOf("dt", "duration", "durationMs") ?: 0L
    )
}

internal fun extractNeteaseHotSearchKeywords(data: JsonElement?): List<String> {
    val root = data as? JsonObject ?: return emptyList()
    val keywordLists = listOfNotNull(
        root.getIgnoreCase("data") as? JsonArray,
        ((root.getIgnoreCase("result") as? JsonObject)?.getIgnoreCase("hots") as? JsonArray),
        ((root.getIgnoreCase("result") as? JsonObject)?.getIgnoreCase("hot") as? JsonArray),
        root.getIgnoreCase("hots") as? JsonArray
    )

    keywordLists.forEach { keywords ->
        val parsed = keywords.mapNotNull { item ->
            when (item) {
                is JsonObject -> item.firstStringOf(
                    "searchWord", "word", "first", "keyword", "name", "content"
                )?.trim()

                is JsonPrimitive -> item.contentOrNull?.trim()
                else -> null
            }
        }.filter { it.isNotBlank() }
            .distinct()

        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}

internal fun extractNeteaseDefaultKeyword(data: JsonElement?): String? {
    val root = data as? JsonObject ?: return null
    val payload = root.getIgnoreCase("data") as? JsonObject ?: return null
    return payload.firstStringOf("realkeyword", "showKeyword", "keyword")
        ?.replace("🔥", "")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

internal fun pickNeteaseHotKeywordToplist(toplists: List<ToplistInfo>): ToplistInfo? {
    if (toplists.isEmpty()) return null
    val priorities = listOf("热歌", "飙升", "新歌", "原创", "流行")
    return priorities.firstNotNullOfOrNull { token ->
        toplists.firstOrNull { it.name.contains(token) }
    } ?: toplists.first()
}

internal fun SearchType.toNeteaseOfficialSearchType(): Int = when (this) {
    SearchType.SONG -> 1
    SearchType.ARTIST -> 100
    SearchType.ALBUM -> 10
    SearchType.PLAYLIST -> 1000
}

internal fun extractNeteaseSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val result = ((data as? JsonObject)?.getIgnoreCase("result") as? JsonObject) ?: return emptyList()
    val items = when (type) {
        SearchType.ARTIST -> extractNestedArray(result, "artists", "artist")
        SearchType.ALBUM -> extractNestedArray(result, "albums", "album")
        SearchType.PLAYLIST -> extractNestedArray(result, "playlists", "playlist")
        SearchType.SONG -> null
    } ?: return emptyList()

    return items.mapNotNull { element ->
        when (type) {
            SearchType.ARTIST -> parseNeteaseArtistSearchItem(element as? JsonObject)
            SearchType.ALBUM -> parseNeteaseAlbumSearchItem(element as? JsonObject)
            SearchType.PLAYLIST -> parseNeteasePlaylistSearchItem(element as? JsonObject)
            SearchType.SONG -> null
        }
    }
}

private fun parseNeteaseArtistSearchItem(item: JsonObject?): SearchResultItem? {
    val artist = item ?: return null
    val id = artist.firstStringOf("id") ?: return null
    val name = artist.firstStringOf("name")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        coverUrl = artist.firstStringOf("picUrl", "img1v1Url", "coverImgUrl").orEmpty(),
        platform = Platform.NETEASE,
        type = SearchType.ARTIST,
        trackCount = artist.firstLongOf("musicSize", "songCount", "trackCount").toSearchCount()
    )
}

private fun parseNeteaseAlbumSearchItem(item: JsonObject?): SearchResultItem? {
    val album = item ?: return null
    val id = album.firstStringOf("id") ?: return null
    val name = album.firstStringOf("name")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = extractJoinedNames(album.getIgnoreCase("artists") ?: album.getIgnoreCase("artist")),
        coverUrl = album.firstStringOf("picUrl", "blurPicUrl", "coverImgUrl").orEmpty(),
        platform = Platform.NETEASE,
        type = SearchType.ALBUM,
        trackCount = album.firstLongOf("size", "songCount", "trackCount").toSearchCount()
    )
}

private fun parseNeteasePlaylistSearchItem(item: JsonObject?): SearchResultItem? {
    val playlist = item ?: return null
    val id = playlist.firstStringOf("id") ?: return null
    val name = playlist.firstStringOf("name")?.trim().orEmpty()
    if (name.isBlank()) return null

    val creator = playlist.getIgnoreCase("creator") as? JsonObject
    return SearchResultItem(
        id = id,
        title = name,
        subtitle = creator?.firstStringOf("nickname").orEmpty(),
        coverUrl = playlist.firstStringOf("coverImgUrl", "picUrl", "imgUrl").orEmpty(),
        platform = Platform.NETEASE,
        type = SearchType.PLAYLIST,
        trackCount = playlist.firstLongOf("trackCount", "songCount", "count").toSearchCount()
    )
}

internal fun extractNeteaseArtistAlbums(data: JsonElement?): List<ArtistAlbum> {
    val root = data as? JsonObject ?: return emptyList()
    val hotAlbums = root.getIgnoreCase("hotAlbums") as? JsonArray ?: return emptyList()
    return hotAlbums.mapNotNull { el ->
        val album = el as? JsonObject ?: return@mapNotNull null
        val id = album.firstStringOf("id") ?: return@mapNotNull null
        ArtistAlbum(
            id = id,
            name = album.firstStringOf("name").orEmpty(),
            coverUrl = album.firstStringOf("picUrl", "blurPicUrl").orEmpty(),
            publishTime = album.firstStringOf("publishTime").orEmpty(),
            songCount = (album.firstLongOf("size") ?: 0L).toInt()
        )
    }
}

internal data class NeteaseArtistEnhancedMetadata(
    val description: String = "",
    val tags: List<String> = emptyList()
)

internal suspend fun fetchNeteaseEnhancedArtistMetadata(
    artistId: String,
    preferences: PlayerPreferences?,
    enhancedApi: NeteaseCloudApiEnhancedApi?
): NeteaseArtistEnhancedMetadata? {
    val configuredPreferences = preferences ?: return null
    val configuredApi = enhancedApi ?: return null
    val baseUrl = configuredPreferences.neteaseCloudApiBaseUrl.first()
    if (baseUrl.isBlank()) return null

    val artistDetailResponse = runCatching {
        configuredApi.artistDetail(
            url = buildNeteaseEnhancedEndpoint(baseUrl, "artist/detail"),
            id = artistId
        )
    }.getOrNull()

    val artistDescResponse = runCatching {
        configuredApi.artistDesc(
            url = buildNeteaseEnhancedEndpoint(baseUrl, "artist/desc"),
            id = artistId
        )
    }.getOrNull()

    if (artistDetailResponse == null && artistDescResponse == null) return null

    return NeteaseArtistEnhancedMetadata(
        description = extractNeteaseEnhancedArtistDescription(artistDescResponse),
        tags = extractNeteaseEnhancedArtistTags(artistDetailResponse)
    )
}

private fun buildNeteaseEnhancedEndpoint(baseUrl: String, path: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return "$trimmed/$path"
}

private fun extractNeteaseEnhancedArtistDescription(response: JsonElement?): String {
    val root = response as? JsonObject ?: return ""
    val code = extractApiCode(root)
    if (code != null && code != 200) return ""

    val briefDesc = root.firstStringOf("briefDesc").orEmpty().trim()
    val introduction = (root.getIgnoreCase("introduction") as? JsonArray)
        ?.mapNotNull { section ->
            val obj = section as? JsonObject ?: return@mapNotNull null
            listOf(
                obj.firstStringOf("ti", "title").orEmpty().trim(),
                obj.firstStringOf("txt", "text").orEmpty().trim()
            ).filter(String::isNotBlank).joinToString("\n")
                .takeIf(String::isNotBlank)
        }
        .orEmpty()
        .distinct()

    return listOfNotNull(
        briefDesc.takeIf(String::isNotBlank),
        introduction.joinToString("\n\n").takeIf(String::isNotBlank)
    ).distinct().joinToString("\n\n")
}

private fun extractNeteaseEnhancedArtistTags(response: JsonElement?): List<String> {
    val root = response as? JsonObject ?: return emptyList()
    val code = extractApiCode(root)
    if (code != null && code != 200) return emptyList()

    val data = root.getIgnoreCase("data") as? JsonObject ?: return emptyList()
    val artist = data.getIgnoreCase("artist") as? JsonObject
    val identify = data.getIgnoreCase("identify") as? JsonObject
    val secondaryIdentity = data.getIgnoreCase("secondaryExpertIdentiy")
        ?: data.getIgnoreCase("secondaryExpertIdentity")

    return buildList {
        addAll(extractTextList(artist?.getIgnoreCase("identities")))
        addAll(extractTextList(artist?.getIgnoreCase("musicIdentityTags")))
        addAll(extractTextList(identify?.getIgnoreCase("imageDesc")))
        addAll(extractTextList(secondaryIdentity, "expertIdentiyName", "expertIdentityName", "name", "title"))
    }.map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}
