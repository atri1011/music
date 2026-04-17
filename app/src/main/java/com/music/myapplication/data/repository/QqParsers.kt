package com.music.myapplication.data.repository

import com.music.myapplication.domain.model.ArtistAlbum
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal fun buildQqToplistsRequestBody(): JsonElement = buildJsonObject {
    put(
        "comm",
        buildJsonObject {
            put("cv", 4747474)
            put("ct", 24)
            put("format", "json")
            put("inCharset", "utf-8")
            put("outCharset", "utf-8")
            put("uin", 0)
        }
    )
    put(
        "toplist",
        buildJsonObject {
            put("module", "musicToplist.ToplistInfoServer")
            put("method", "GetAll")
            put("param", buildJsonObject { })
        }
    )
}

internal fun buildQqToplistDetailRequestBody(topId: Int): JsonElement = buildJsonObject {
    put(
        "comm",
        buildJsonObject {
            put("cv", 4747474)
            put("ct", 24)
            put("format", "json")
            put("inCharset", "utf-8")
            put("outCharset", "utf-8")
            put("uin", 0)
        }
    )
    put(
        "toplist",
        buildJsonObject {
            put("module", "musicToplist.ToplistInfoServer")
            put("method", "GetDetail")
            put(
                "param",
                buildJsonObject {
                    put("topid", topId)
                    put("num", 300)
                    put("period", "")
                }
            )
        }
    )
}

internal fun extractQqToplists(data: JsonElement?): List<ToplistInfo> {
    val root = data as? JsonObject ?: return emptyList()
    val groups = (((root.getIgnoreCase("toplist") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject)
        ?.getIgnoreCase("group") as? JsonArray) ?: return emptyList()

    val toplists = mutableListOf<ToplistInfo>()
    groups.forEach { groupElement ->
        val group = groupElement as? JsonObject ?: return@forEach
        val items = group.getIgnoreCase("toplist") as? JsonArray ?: return@forEach
        items.forEach { itemElement ->
            val item = itemElement as? JsonObject ?: return@forEach
            val id = item.firstStringOf("topId", "topid", "id").orEmpty()
            val name = item.firstStringOf("title", "name").orEmpty()
            if (id.isBlank() || name.isBlank()) return@forEach
            val coverUrl = normalizeQqImageUrl(
                item.firstStringOf("headPicUrl", "frontPicUrl", "picUrl", "pic").orEmpty()
            )
            toplists += ToplistInfo(
                id = id,
                name = name,
                coverUrl = coverUrl
            )
        }
    }

    return toplists
}

internal fun extractQqToplistSongCoverMap(data: JsonElement?): Map<String, String> {
    val root = data as? JsonObject ?: return emptyMap()
    val songs = (((root.getIgnoreCase("toplist") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject)
        ?.getIgnoreCase("songInfoList") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songMid = song.firstStringOf("mid", "songmid", "songMid").orEmpty()
        val songId = song.firstStringOf("id", "songId", "songid").orEmpty()
        if (songMid.isBlank() && songId.isBlank()) return@forEach

        val album = song.getIgnoreCase("album") as? JsonObject
        val albumMid = album?.firstStringOf("mid") ?: song.firstStringOf("albumMid", "albummid")
        val coverUrl = buildQqAlbumCoverUrl(albumMid.orEmpty())
        if (coverUrl.isNotBlank()) {
            if (songMid.isNotBlank()) coverMap[songMid] = coverUrl
            if (songId.isNotBlank()) coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun extractQqSongCoverMap(data: JsonElement?): Map<String, String> {
    val songs = ((data as? JsonObject)?.getIgnoreCase("data") as? JsonArray) ?: return emptyMap()

    val coverMap = linkedMapOf<String, String>()
    songs.forEach { songElement ->
        val song = songElement as? JsonObject ?: return@forEach
        val songMid = song.firstStringOf("mid", "songmid", "songMid").orEmpty()
        val songId = song.firstStringOf("id", "songId", "songid").orEmpty()
        if (songMid.isBlank() && songId.isBlank()) return@forEach

        val album = song.getIgnoreCase("album") as? JsonObject
        val albumMid = album?.firstStringOf("mid") ?: song.firstStringOf("albumMid", "albummid")
        val coverUrl = buildQqAlbumCoverUrl(albumMid.orEmpty())
        if (coverUrl.isNotBlank()) {
            if (songMid.isNotBlank()) coverMap[songMid] = coverUrl
            if (songId.isNotBlank()) coverMap[songId] = coverUrl
        }
    }
    return coverMap
}

internal fun extractFirstQqSongId(data: JsonElement?): String? {
    val root = data as? JsonObject ?: return null
    val candidates = buildList {
        add(root.getIgnoreCase("data"))
        add(root.getIgnoreCase("songlist"))
        add((root.getIgnoreCase("song") as? JsonObject)?.getIgnoreCase("data"))
    }

    candidates.forEach { container ->
        val items = container as? JsonArray ?: return@forEach
        items.forEach { item ->
            val songId = (item as? JsonObject)
                ?.firstStringOf("id", "songid", "songId")
                ?.takeIf { it.isNotBlank() }
            if (songId != null) return songId
        }
    }

    return null
}

internal fun extractQqMvVid(data: JsonElement?): String? {
    val song = ((data as? JsonObject)?.getIgnoreCase("data") as? JsonArray)
        ?.firstOrNull() as? JsonObject ?: return null
    val mv = song.getIgnoreCase("mv") as? JsonObject
    return mv?.firstStringOf("vid", "mv_id", "mvId")
        ?.takeIf { it.isNotBlank() && it != "0" }
        ?: song.firstStringOf("mvvid", "mvVid")
            ?.takeIf { it.isNotBlank() && it != "0" }
}

internal fun extractQqMvUrl(data: JsonElement?, vid: String): String? {
    val root = data as? JsonObject ?: return null
    val candidates = buildList<JsonElement?> {
        add(
            (((root.getIgnoreCase("mvUrl") as? JsonObject)
                ?.getIgnoreCase("data") as? JsonObject)
                ?.getIgnoreCase(vid))
        )
        add(
            (((root.getIgnoreCase("mvInfo") as? JsonObject)
                ?.getIgnoreCase("data") as? JsonObject)
                ?.getIgnoreCase(vid))
        )
        add(root.getIgnoreCase("mvUrl"))
    }

    candidates.forEach { candidate ->
        when (candidate) {
            is JsonArray -> candidate.forEach { nested ->
                val url = (nested as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }
                if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
            }

            is JsonObject -> {
                val directList = candidate.getIgnoreCase("freeflow_url") as? JsonArray
                directList?.forEach { item ->
                    val url = (item as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.startsWith("http", ignoreCase = true) }
                    if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
                }

                extractFirstVideoLikeUrl(candidate)?.let { return normalizeVideoLikeUrl(it) }
            }

            is JsonPrimitive -> {
                val url = candidate.contentOrNull?.takeIf { it.startsWith("http", ignoreCase = true) }
                if (!url.isNullOrBlank()) return normalizeVideoLikeUrl(url)
            }

            else -> Unit
        }
    }

    return null
}

internal fun mergeQqSearchResultItems(vararg groups: List<SearchResultItem>): List<SearchResultItem> {
    val mergedItems = LinkedHashMap<String, SearchResultItem>()
    groups.asList().flatten().forEach { item ->
        val key = item.id.ifBlank {
            "${item.type.name}:${item.title.normalizeComparisonText()}:${item.subtitle.normalizeComparisonText()}"
        }
        val existing = mergedItems[key]
        mergedItems[key] = if (existing == null) item else existing.mergeWith(item)
    }
    return mergedItems.values.toList()
}

private fun SearchResultItem.mergeWith(other: SearchResultItem): SearchResultItem {
    return copy(
        subtitle = subtitle.ifBlank { other.subtitle },
        coverUrl = coverUrl.ifBlank { other.coverUrl },
        trackCount = if (trackCount > 0) trackCount else other.trackCount,
        extra = extra.ifBlank { other.extra }
    )
}

internal fun rankQqAlbumSearchResults(keyword: String, results: List<SearchResultItem>): List<SearchResultItem> {
    val normalizedKeyword = keyword.normalizeComparisonText()
    if (normalizedKeyword.isBlank() || results.size < 2) return results

    return results.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<SearchResultItem>> {
                scoreQqAlbumSearchResult(normalizedKeyword, it.value)
            }.thenBy { it.index }
        )
        .map { it.value }
}

private fun scoreQqAlbumSearchResult(normalizedKeyword: String, item: SearchResultItem): Int {
    val normalizedTitle = item.title.normalizeComparisonText()
    val normalizedSubtitle = item.subtitle.normalizeComparisonText()
    return when {
        normalizedTitle == normalizedKeyword -> 500
        normalizedTitle.startsWith(normalizedKeyword) -> 400
        normalizedTitle.contains(normalizedKeyword) -> 300
        normalizedSubtitle == normalizedKeyword -> 200
        normalizedSubtitle.contains(normalizedKeyword) -> 100
        else -> 0
    }
}

internal fun buildQqAlbumCoverUrl(albumMid: String): String {
    if (albumMid.isBlank()) return ""
    return "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
}

internal fun buildQqSingerCoverUrl(singerMid: String): String {
    if (singerMid.isBlank()) return ""
    return "https://y.qq.com/music/photo_new/T001R300x300M000$singerMid.jpg"
}

internal fun normalizeQqImageUrl(rawUrl: String): String {
    val url = rawUrl.trim()
    if (url.isBlank()) return ""
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://", ignoreCase = true) -> "https://${url.removePrefix("http://")}"
        else -> url
    }
}

internal fun SearchType.toQqOfficialSearchType(): Int = when (this) {
    SearchType.SONG -> 0
    SearchType.ARTIST -> 1
    SearchType.ALBUM -> 2
    SearchType.PLAYLIST -> 3
}

internal fun buildQqSearchByTypeBody(
    keyword: String,
    page: Int,
    pageSize: Int,
    type: SearchType
): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 11)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("search", buildJsonObject {
        put("module", "music.search.SearchCgiService")
        put("method", "DoSearchForQQMusicMobile")
        put("param", buildJsonObject {
            put("searchid", buildQqSearchId())
            put("query", keyword)
            put("search_type", type.toQqOfficialSearchType())
            put("page_num", page.coerceAtLeast(1))
            put("num_per_page", pageSize.coerceIn(1, 50))
            put("highlight", 1)
            put("nqc_flag", 0)
            put("grp", 1)
        })
    })
}

internal fun buildQqGeneralSearchBody(
    keyword: String,
    page: Int,
    pageSize: Int
): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("req", buildJsonObject {
        put("method", "do_search_v2")
        put("module", "music.adaptor.SearchAdaptor")
        put("param", buildJsonObject {
            put("query", keyword)
            put("search_type", 100)
            put("page_num", page.coerceAtLeast(1))
            put("num_per_page", pageSize.coerceIn(1, 50))
            put("highlight", true)
            put("grp", 1)
        })
    })
}

internal fun buildQqMvRequestBody(vid: String): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("mvInfo", buildJsonObject {
        put("module", "video.VideoDataServer")
        put("method", "getMVInfo")
        put("param", buildJsonObject {
            put("vidlist", JsonArray(listOf(JsonPrimitive(vid))))
        })
    })
    put("mvUrl", buildJsonObject {
        put("module", "music.stream.MvUrlProxy")
        put("method", "GetMvUrls")
        put("param", buildJsonObject {
            put("vids", JsonArray(listOf(JsonPrimitive(vid))))
            put("request_typet", 10001)
            put("addrtype", 3)
            put("format", 264)
        })
    })
}

private fun buildQqSearchId(): String {
    val now = System.currentTimeMillis()
    val suffix = (now % 1_000_000).toString().padStart(6, '0')
    return "$now$suffix"
}

internal fun extractQqSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val body = extractQqSearchBody(data) ?: return emptyList()
    val items = when (type) {
        SearchType.ARTIST -> extractQqSearchItems(
            body,
            "singer", "item_singer", "zhida", "singerlist", "itemlist", "list", "zhida_singer"
        )

        SearchType.ALBUM -> extractQqSearchItems(
            body,
            "album", "item_album", "albumlist", "itemlist", "list"
        )

        SearchType.PLAYLIST -> extractQqSearchItems(
            body,
            "songlist", "item_songlist", "playlist", "itemlist", "list"
        )

        SearchType.SONG -> emptyList()
    }

    return items.mapNotNull { element ->
        when (type) {
            SearchType.ARTIST -> parseQqArtistSearchItem(element as? JsonObject)
            SearchType.ALBUM -> parseQqAlbumSearchItem(element as? JsonObject)
            SearchType.PLAYLIST -> parseQqPlaylistSearchItem(element as? JsonObject)
            SearchType.SONG -> null
        }
    }
}

internal fun extractQqDirectSearchResults(
    data: JsonElement?,
    type: SearchType
): List<SearchResultItem> {
    val body = extractQqSearchBody(data) ?: return emptyList()
    val items = (((body.getIgnoreCase("direct_result") as? JsonObject)
        ?.getIgnoreCase("items")) as? JsonArray).orEmpty()

    val restype = when (type) {
        SearchType.ARTIST -> "singer"
        SearchType.ALBUM -> "album"
        SearchType.PLAYLIST -> "songlist"
        SearchType.SONG -> return emptyList()
    }

    return items.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        if (!item.firstStringOf("restype").orEmpty().equals(restype, ignoreCase = true)) {
            return@mapNotNull null
        }
        when (type) {
            SearchType.ARTIST -> parseQqArtistSearchItem(item)
            SearchType.ALBUM -> parseQqAlbumSearchItem(item)
            SearchType.PLAYLIST -> parseQqPlaylistSearchItem(item)
            SearchType.SONG -> null
        }
    }
}

internal fun extractQqSingerAlbumSearchResults(data: JsonElement?): List<SearchResultItem> {
    val albumList = (((data as? JsonObject)?.getIgnoreCase("albums") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject)
        ?.getIgnoreCase("albumList") as? JsonArray ?: return emptyList()

    return albumList.mapNotNull { element ->
        parseQqAlbumSearchItem(element as? JsonObject)
    }
}

internal fun extractQqAlbumSearchResultsFromSongs(data: JsonElement?): List<SearchResultItem> {
    val body = extractQqSearchBody(data) ?: return emptyList()
    val songNode = body.getIgnoreCase("item_song") ?: body.getIgnoreCase("song") ?: return emptyList()
    val songItems = extractQqSearchItems(songNode, "items", "list")
    return songItems.mapNotNull { element ->
        parseQqAlbumSearchItemFromSong(element as? JsonObject)
    }
}

private fun parseQqArtistSearchItem(item: JsonObject?): SearchResultItem? {
    val artist = item ?: return null
    val customInfo = artist.getIgnoreCase("custom_info") as? JsonObject
    val id = customInfo?.firstStringOf("mid", "singer_mid")
        ?: artist.firstStringOf("singerMID", "singerMid", "singer_mid", "singerid", "mid", "id")
        ?: return null
    val name = artist.firstStringOf("singerName", "singer_name", "name", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        coverUrl = normalizeQqImageUrl(artist.firstStringOf("singerPic", "pic", "avatar", "imgurl").orEmpty()),
        platform = Platform.QQ,
        type = SearchType.ARTIST,
        trackCount = (
            customInfo?.firstLongOf("song_num", "songNum")
                ?: artist.firstLongOf("songNum", "songnum", "musicNum", "song_count", "totalSongNum")
            ).toSearchCount()
    )
}

private fun parseQqAlbumSearchItem(item: JsonObject?): SearchResultItem? {
    val album = item ?: return null
    val customInfo = album.getIgnoreCase("custom_info") as? JsonObject
    val albumMid = customInfo?.firstStringOf("mid")
        ?: album.firstStringOf("albumMID", "albumMid", "albummid", "mid")
    val id = album.firstStringOf("albumID", "albumId", "id") ?: albumMid ?: return null
    val name = album.firstStringOf("albumName", "album_name", "name", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    val subtitle = album.firstStringOf("singerName", "singer_name", "artistName", "artist", "singer").orEmpty()
        .ifBlank {
            customInfo?.firstStringOf("quality_album_title_prefix")
                .orEmpty()
        }.ifBlank {
            extractJoinedNames(album.getIgnoreCase("singerList") ?: album.getIgnoreCase("singer"))
        }
    val coverUrl = album.firstStringOf("albumPic", "pic", "imgurl")
        ?.let(::normalizeQqImageUrl)
        .orEmpty()
        .ifBlank { buildQqAlbumCoverUrl(albumMid.orEmpty()) }

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = subtitle,
        coverUrl = coverUrl,
        platform = Platform.QQ,
        type = SearchType.ALBUM,
        trackCount = (customInfo?.firstLongOf("track_num")
            ?: album.firstLongOf("song_count", "songNum", "songnum", "musicNum", "trackCount", "totalNum")).toSearchCount()
    )
}

private fun parseQqAlbumSearchItemFromSong(item: JsonObject?): SearchResultItem? {
    val song = item ?: return null
    val album = song.getIgnoreCase("album") as? JsonObject ?: return null
    val albumMid = album.firstStringOf("mid", "albumMID", "albumMid", "albummid")
    val id = album.firstStringOf("id", "albumID", "albumId") ?: albumMid ?: return null
    val name = album.firstStringOf("name", "title", "albumName")?.trim().orEmpty()
    if (name.isBlank()) return null

    val subtitle = extractJoinedNames(song.getIgnoreCase("singer") ?: song.getIgnoreCase("singerList"))
        .ifBlank { song.firstStringOf("singerName", "artistName").orEmpty() }

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = subtitle,
        coverUrl = buildQqAlbumCoverUrl(albumMid.orEmpty()),
        platform = Platform.QQ,
        type = SearchType.ALBUM
    )
}

private fun parseQqPlaylistSearchItem(item: JsonObject?): SearchResultItem? {
    val playlist = item ?: return null
    val id = playlist.firstStringOf("dissid", "tid", "id") ?: return null
    val name = playlist.firstStringOf("dissname", "diss_name", "name", "title")?.trim().orEmpty()
    if (name.isBlank()) return null

    return SearchResultItem(
        id = id,
        title = name,
        subtitle = playlist.firstStringOf("creatorName", "nick", "nickname").orEmpty(),
        coverUrl = normalizeQqImageUrl(
            playlist.firstStringOf("imgurl", "cover_url_big", "coverUrl", "logo").orEmpty()
        ),
        platform = Platform.QQ,
        type = SearchType.PLAYLIST,
        trackCount = playlist.firstLongOf("song_count", "songNum", "songnum", "total_song_num").toSearchCount()
    )
}

private fun extractQqSearchBody(data: JsonElement?): JsonObject? {
    val root = data as? JsonObject ?: return null
    val candidates = buildList {
        add(root)
        root.values.forEach { value ->
            if (value is JsonObject) add(value)
        }
    }

    candidates.forEach { candidate ->
        (candidate.getIgnoreCase("body") as? JsonObject)?.let { return it }
        val dataNode = candidate.getIgnoreCase("data") as? JsonObject ?: return@forEach
        (dataNode.getIgnoreCase("body") as? JsonObject)?.let { return it }
    }
    return null
}

private fun extractQqSearchItems(node: JsonElement?, vararg preferredKeys: String): List<JsonElement> {
    return when (node) {
        is JsonArray -> node.toList()
        is JsonObject -> {
            preferredKeys.forEach { key ->
                val child = node.getIgnoreCase(key) ?: return@forEach
                val items = extractQqSearchItems(child, *preferredKeys)
                if (items.isNotEmpty()) return items
                if (child is JsonObject && child.isQqSearchTerminalItem()) {
                    return listOf(child)
                }
            }
            if (node.isQqSearchTerminalItem()) listOf(node) else emptyList()
        }

        else -> emptyList()
    }
}

private fun JsonObject.isQqSearchTerminalItem(): Boolean {
    return firstStringOf(
        "singerMID", "singerMid", "singer_mid", "albumMID", "albumMid",
        "albummid", "dissid", "tid", "mid", "id"
    ) != null
}

internal fun buildQqArtistDetailBody(singerMid: String): JsonElement = buildJsonObject {
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("songs", buildJsonObject {
        put("module", "musichall.song_list_server")
        put("method", "GetSingerSongList")
        put("param", buildJsonObject {
            put("singerMid", singerMid)
            put("order", 1)
            put("begin", 0)
            put("num", 50)
        })
    })
    put("albums", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumListServer")
        put("method", "GetAlbumList")
        put("param", buildJsonObject {
            put("singerMid", singerMid)
            put("order", 1)
            put("begin", 0)
            put("num", 20)
        })
    })
}

internal fun buildQqSingerAlbumsBody(singerMid: String, page: Int, pageSize: Int): JsonElement = buildJsonObject {
    val safePage = page.coerceAtLeast(1)
    val safePageSize = pageSize.coerceIn(1, 50)
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("albums", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumListServer")
        put("method", "GetAlbumList")
        put("param", buildJsonObject {
            put("singerMid", singerMid)
            put("order", 1)
            put("begin", (safePage - 1) * safePageSize)
            put("num", safePageSize)
        })
    })
}

private const val QQ_DEFAULT_PLAYLIST_CATEGORY_ID = 6

internal val qqPlaylistCategoryIdMap = linkedMapOf(
    "流行" to 6, "经典" to 22, "轻音乐" to 12, "摇滚" to 19,
    "民谣" to 8, "电子" to 14, "嘻哈" to 25, "R&B" to 17, "古典" to 7
)

internal fun buildQqPlaylistByCategoryBody(category: String, page: Int, pageSize: Int): JsonElement = buildJsonObject {
    val categoryId = qqPlaylistCategoryIdMap[category] ?: QQ_DEFAULT_PLAYLIST_CATEGORY_ID
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("playlist", buildJsonObject {
        put("module", "playlist.PlayListPlazaServer")
        put("method", "get_playlist_by_category")
        put("param", buildJsonObject {
            put("id", categoryId)
            put("curPage", page.coerceAtLeast(1))
            put("size", pageSize.coerceAtLeast(1))
            put("order", 5)
            put("titleid", categoryId)
        })
    })
}

internal fun buildQqPlaylistDetailBody(playlistId: String, songNum: Int): JsonElement = buildJsonObject {
    val safeSongNum = songNum.coerceIn(1, 500)
    val numericPlaylistId = playlistId.toLongOrNull()
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("playlist", buildJsonObject {
        put("module", "music.srfDissInfo.aiDissInfo")
        put("method", "uniform_get_Dissinfo")
        put("param", buildJsonObject {
            if (numericPlaylistId != null) {
                put("disstid", numericPlaylistId)
            } else {
                put("disstid", playlistId)
            }
            put("tag", 1)
            put("song_begin", 0)
            put("song_num", safeSongNum)
        })
    })
}

internal fun extractQqArtistSong(song: JsonObject?): Track? {
    val item = ((song?.getIgnoreCase("songInfo")) ?: song) as? JsonObject ?: return null
    val mid = item.firstStringOf("mid", "songmid") ?: return null
    val singers = item.get("singer") as? JsonArray
    val artist = singers?.mapNotNull { (it as? JsonObject)?.firstStringOf("name") }?.joinToString("/").orEmpty()
    val albumObj = item.get("album") as? JsonObject
    val albumMid = albumObj?.firstStringOf("mid").orEmpty()
    return Track(
        id = mid,
        platform = Platform.QQ,
        title = item.firstStringOf("name", "title").orEmpty(),
        artist = artist,
        album = albumObj?.firstStringOf("name").orEmpty(),
        albumId = albumObj?.firstStringOf("id", "albumID", "albumId", "mid", "albumMID", "albumMid", "albummid").orEmpty(),
        coverUrl = buildQqAlbumCoverUrl(albumMid),
        durationMs = (item.firstLongOf("interval") ?: 0L) * 1000
    )
}

internal fun extractQqAlbumTracks(data: JsonElement?): List<Track> {
    val songs = ((((data as? JsonObject)?.get("songs") as? JsonObject)
        ?.get("data") as? JsonObject)
        ?.get("songList") as? JsonArray) ?: return emptyList()
    return songs.mapNotNull { extractQqArtistSong(it as? JsonObject) }
}

internal fun extractQqPlaylistTracks(data: JsonElement?): List<Track> {
    val playlistData = (((data as? JsonObject)?.getIgnoreCase("playlist") as? JsonObject)
        ?.getIgnoreCase("data") as? JsonObject) ?: return emptyList()
    val songs = (playlistData.getIgnoreCase("songlist")
        ?: playlistData.getIgnoreCase("songList")
        ?: playlistData.getIgnoreCase("list")) as? JsonArray ?: return emptyList()
    return songs.mapNotNull { extractQqArtistSong(it as? JsonObject) }
}

internal fun extractQqArtistAlbum(album: JsonObject?): ArtistAlbum? {
    val item = album ?: return null
    val id = item.firstStringOf("albumID", "albumId", "id") ?: return null
    val albumMid = item.firstStringOf("albumMid", "albumMID", "mid").orEmpty()
    return ArtistAlbum(
        id = id,
        name = item.firstStringOf("albumName", "name", "title").orEmpty(),
        coverUrl = buildQqAlbumCoverUrl(albumMid),
        publishTime = item.firstStringOf("publishDate", "public_time", "time_public").orEmpty(),
        songCount = (item.firstLongOf("totalNum", "track_num", "song_count") ?: 0L).toInt()
    )
}
