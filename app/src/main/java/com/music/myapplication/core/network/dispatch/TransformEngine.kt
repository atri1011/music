package com.music.myapplication.core.network.dispatch

import com.music.myapplication.data.remote.dto.TransformRuleDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransformEngine @Inject constructor(private val json: Json) {

    fun transform(responseBody: String, rule: JsonElement?, platform: Platform): List<Track> {
        val root = json.parseToJsonElement(responseBody)
        parseRule(rule)?.let { parsedRule ->
            val mapped = transformByRule(root, parsedRule, platform)
            if (mapped.isNotEmpty()) return mapped
        }
        return fallbackTransform(root, platform)
    }

    private fun transformByRule(root: JsonElement, rule: TransformRuleDto, platform: Platform): List<Track> {
        val items = navigateToRoot(root, rule.root)
        if (items !is JsonArray) return emptyList()
        return items.mapNotNull { element ->
            if (element !is JsonObject) return@mapNotNull null
            mapToTrack(element, rule.fields, platform)
        }
    }

    private fun parseRule(rule: JsonElement?): TransformRuleDto? {
        if (rule == null) return null
        return when (rule) {
            is JsonObject -> parseRuleFromObject(rule)
            is JsonPrimitive -> {
                val raw = rule.contentOrNull?.trim().orEmpty()
                if (!raw.startsWith("{") || !raw.endsWith("}")) return null
                runCatching {
                    parseRuleFromObject(json.decodeFromString<JsonObject>(raw))
                }.getOrNull()
            }
            else -> null
        }
    }

    private fun parseRuleFromObject(obj: JsonObject): TransformRuleDto? {
        val root = findStringByKeys(obj, listOf("root", "path", "itemsPath", "listPath")).orEmpty()
        val fieldsObj = (obj["fields"] ?: obj["mapping"] ?: obj["map"]) as? JsonObject
        val fields = if (fieldsObj != null) {
            fieldsObj.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }.toMap()
        } else {
            listOf("id", "title", "artist", "album", "albumId", "coverUrl", "durationMs")
                .mapNotNull { field ->
                    findStringByKeys(obj, listOf(field))?.let { field to it }
                }.toMap()
        }
        if (root.isBlank() && fields.isEmpty()) return null
        return TransformRuleDto(root = root, fields = fields)
    }

    private fun fallbackTransform(root: JsonElement, platform: Platform): List<Track> {
        val arrays = mutableListOf<JsonArray>()
        collectArrays(root, arrays, depth = 0)
        val best = arrays
            .map { it to scoreArray(it) }
            .maxByOrNull { (_, score) -> score }
            ?.takeIf { (_, score) -> score > 0 }
            ?.first
            ?: return emptyList()
        return best.mapNotNull { element ->
            (element as? JsonObject)?.let { mapWithAliases(it, platform) }
        }
    }

    private fun collectArrays(element: JsonElement, out: MutableList<JsonArray>, depth: Int) {
        if (depth > 12) return
        when (element) {
            is JsonArray -> {
                out += element
                element.forEach { child -> collectArrays(child, out, depth + 1) }
            }
            is JsonObject -> {
                element.values.forEach { child -> collectArrays(child, out, depth + 1) }
            }
            else -> Unit
        }
    }

    private fun scoreArray(array: JsonArray): Int {
        val objects = array.filterIsInstance<JsonObject>()
        if (objects.isEmpty()) return Int.MIN_VALUE
        val sample = objects.take(8)
        val score = sample.sumOf { scoreObject(it) }
        return score * 10 + minOf(objects.size, 50)
    }

    private fun scoreObject(obj: JsonObject): Int {
        var score = 0
        if (findStringByKeys(obj, ID_KEYS) != null) score += 4
        if (findStringByKeys(obj, TITLE_KEYS) != null) score += 4
        if (findStringByKeys(obj, COVER_KEYS) != null) score += 2
        if (findStringByKeys(obj, ARTIST_KEYS) != null) score += 1
        return score
    }

    private fun mapWithAliases(obj: JsonObject, platform: Platform): Track? {
        val id = resolveAliasId(obj, platform) ?: return null
        val title = findStringByKeys(obj, TITLE_KEYS) ?: ""
        val artist = findStringByKeys(obj, ARTIST_KEYS) ?: ""
        val album = findStringByKeys(obj, ALBUM_KEYS) ?: ""
        val albumId = findStringByKeys(obj, ALBUM_ID_KEYS) ?: ""
        val cover = findStringByKeys(obj, COVER_KEYS) ?: ""
        val duration = findDurationByKeys(obj, DURATION_KEYS) ?: 0L
        return Track(
            id = id,
            platform = platform,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            coverUrl = cover,
            durationMs = duration
        )
    }

    private fun resolveAliasId(obj: JsonObject, platform: Platform): String? {
        val hasArtist = findStringByKeys(obj, ARTIST_KEYS) != null

        val topId = getElementByPath(obj, "topId")?.toText()
        if (!topId.isNullOrBlank() && !hasArtist) {
            return topId
        }

        if (platform == Platform.QQ) {
            val qqSongMid = getElementByPath(obj, "mid")?.toText()
                ?: getElementByPath(obj, "songmid")?.toText()
                ?: getElementByPath(obj, "songMid")?.toText()
                ?: getElementByPath(obj, "file.media_mid")?.toText()
            if (!qqSongMid.isNullOrBlank()) {
                return qqSongMid
            }
        }

        val sourceId = getElementByPath(obj, "sourceid")?.toText()
        val hasToplistShape = !hasArtist && (
            getElementByPath(obj, "source") != null ||
                getElementByPath(obj, "disname") != null ||
                getElementByPath(obj, "info") != null
            )
        if (!sourceId.isNullOrBlank() && hasToplistShape) {
            return sourceId
        }

        return findStringByKeys(obj, ID_KEYS)
    }

    private fun navigateToRoot(element: JsonElement, path: String): JsonElement {
        if (path.isBlank()) return element
        var current = element
        path.split(".").forEach { key ->
            current = when (current) {
                is JsonObject -> (current as JsonObject)[key] ?: return current
                else -> return current
            }
        }
        return current
    }

    private fun mapToTrack(obj: JsonObject, fields: Map<String, String>, platform: Platform): Track? {
        val mappedId = extractString(obj, fields["id"] ?: "id") ?: return null
        val id = resolveAliasId(obj, platform) ?: mappedId
        return Track(
            id = id,
            platform = platform,
            title = extractString(obj, fields["title"] ?: "name") ?: "",
            artist = extractString(obj, fields["artist"] ?: "artist") ?: "",
            album = extractString(obj, fields["album"] ?: "album") ?: "",
            albumId = extractString(obj, fields["albumId"] ?: "albumId")
                ?: findStringByKeys(obj, ALBUM_ID_KEYS)
                ?: "",
            coverUrl = extractString(obj, fields["coverUrl"] ?: "cover") ?: "",
            durationMs = extractLong(obj, fields["durationMs"] ?: "duration") ?: 0L
        )
    }

    private fun extractString(obj: JsonObject, path: String): String? {
        val element = getElementByPath(obj, path) ?: return null
        return element.toText()
    }

    private fun extractLong(obj: JsonObject, path: String): Long? {
        val element = getElementByPath(obj, path) ?: return null
        val primitive = element as? JsonPrimitive
        val raw = primitive?.longOrNull ?: element.toText()?.toLongOrNull() ?: return null
        return if (raw in 1..999L) raw * 1000 else raw
    }

    private fun findStringByKeys(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val value = getElementByPath(obj, key)?.toText()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun findDurationByKeys(obj: JsonObject, keys: List<String>): Long? {
        for (key in keys) {
            val value = extractLong(obj, key)
            if (value != null) return value
        }
        return null
    }

    private fun getElementByPath(element: JsonElement, path: String): JsonElement? {
        if (path.isBlank()) return null
        var current: JsonElement = element
        path.split(".").forEach { key ->
            current = when (current) {
                is JsonObject -> current.getIgnoreCase(key) ?: return null
                else -> return null
            }
        }
        return current
    }

    private fun JsonObject.getIgnoreCase(key: String): JsonElement? {
        return this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    private fun JsonElement.toText(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject -> {
                getIgnoreCase("name")?.toText()
                    ?: getIgnoreCase("title")?.toText()
                    ?: getIgnoreCase("id")?.toText()
            }
            is JsonArray -> this.take(3).mapNotNull { it.toText() }.joinToString("/").ifBlank { null }
        }
    }

    private companion object {
        val ID_KEYS = listOf(
            "id", "rid", "songId", "songid", "trackId", "topId", "topid", "playlistId", "dissid", "sourceid",
            "mid", "songmid", "media_mid"
        )
        val TITLE_KEYS = listOf(
            "title", "name", "songName", "songname", "topTitle", "dissname", "playlistName"
        )
        val ARTIST_KEYS = listOf(
            "artist", "singer", "singerList", "artistName", "singerName", "author", "ar.name"
        )
        val ALBUM_KEYS = listOf(
            "album", "albumName", "albumname", "al.name"
        )
        val ALBUM_ID_KEYS = listOf(
            "albumId", "albumid", "albumID", "al.id", "album.id",
            "album.mid", "albumMid", "albumMID", "albummid", "al.mid"
        )
        val COVER_KEYS = listOf(
            "cover", "coverUrl", "coverImgUrl", "pic", "picUrl", "img", "imgurl", "logo",
            "album.picUrl", "al.picUrl", "headPicUrl", "frontPicUrl", "web_albumpic_short", "web_albumpic_big"
        )
        val DURATION_KEYS = listOf(
            "durationMs", "duration", "dt", "interval", "songTimeMinutes"
        )
    }
}
