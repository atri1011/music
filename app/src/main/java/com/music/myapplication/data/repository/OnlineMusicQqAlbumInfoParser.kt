package com.music.myapplication.data.repository

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val QQ_ALBUM_DETAIL_PAGE_SIZE = 300

internal fun buildQqAlbumInfoBody(idOrMid: String): JsonElement = buildJsonObject {
    val numericAlbumId = idOrMid.toLongOrNull()
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("album", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumInfoServer")
        put("method", "GetAlbumDetail")
        put("param", buildJsonObject {
            if (numericAlbumId != null) {
                put("albumId", numericAlbumId)
            } else {
                put("albumMid", idOrMid)
            }
        })
    })
}

internal fun extractQqAlbumInfoData(data: JsonElement?): JsonObject? {
    return (((data as? JsonObject)?.get("album") as? JsonObject)
        ?.get("data") as? JsonObject)
}

internal fun extractQqAlbumIdFromInfoData(albumData: JsonObject?): String? {
    val basicInfo = albumData?.getIgnoreCase("basicInfo") as? JsonObject ?: return null
    return basicInfo.firstStringOf("albumID", "albumId", "id")
}

internal fun extractQqAlbumId(data: JsonElement?): String? =
    extractQqAlbumIdFromInfoData(extractQqAlbumInfoData(data))

internal fun buildQqAlbumSongListBody(albumId: String): JsonElement = buildJsonObject {
    val numericAlbumId = albumId.toLongOrNull()
    put("comm", buildJsonObject {
        put("cv", 4747474)
        put("ct", 24)
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uin", 0)
    })
    put("songs", buildJsonObject {
        put("module", "music.musichallAlbum.AlbumSongList")
        put("method", "GetAlbumSongList")
        put("param", buildJsonObject {
            if (numericAlbumId != null) {
                put("albumId", numericAlbumId)
            } else {
                put("albumId", albumId)
            }
            put("begin", 0)
            put("num", QQ_ALBUM_DETAIL_PAGE_SIZE)
        })
    })
}
