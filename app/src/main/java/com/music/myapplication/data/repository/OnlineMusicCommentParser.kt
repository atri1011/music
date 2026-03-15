package com.music.myapplication.data.repository

import com.music.myapplication.domain.repository.TrackComment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class ExtractedTrackComments(
    val totalCount: Int = 0,
    val hotComments: List<TrackComment> = emptyList(),
    val latestComments: List<TrackComment> = emptyList(),
    val recommendedComments: List<TrackComment> = emptyList()
) {
    val comments: List<TrackComment>
        get() = when {
            hotComments.isNotEmpty() -> hotComments
            latestComments.isNotEmpty() -> latestComments
            recommendedComments.isNotEmpty() -> recommendedComments
            else -> emptyList()
        }
}

internal fun extractNeteaseTrackComments(data: JsonElement?): ExtractedTrackComments {
    val root = data as? JsonObject ?: return ExtractedTrackComments()
    val hotComments = ((root.getIgnoreCase("hotComments") as? JsonArray).orEmpty())
        .mapNotNull { parseNeteaseTrackComment(it as? JsonObject) }
        .distinctBy(TrackComment::id)
    val latestComments = ((root.getIgnoreCase("comments") as? JsonArray).orEmpty())
        .mapNotNull { parseNeteaseTrackComment(it as? JsonObject) }
        .distinctBy(TrackComment::id)

    val knownCount = maxOf(hotComments.size, latestComments.size).toLong()
    val total = (root.firstLongOf("total") ?: knownCount)
        .coerceAtLeast(knownCount)
        .toInt()

    return ExtractedTrackComments(
        totalCount = total,
        hotComments = hotComments,
        latestComments = latestComments,
        recommendedComments = hotComments
    )
}

internal data class ExtractedCommentPage(
    val totalCount: Int = 0,
    val comments: List<TrackComment> = emptyList()
)

internal fun extractNeteaseSortedTrackComments(data: JsonElement?): ExtractedCommentPage {
    val root = data as? JsonObject ?: return ExtractedCommentPage()
    val commentRoot = (root.getIgnoreCase("data") as? JsonObject) ?: root
    val rawComments = commentRoot.getIgnoreCase("comments") ?: root.getIgnoreCase("comments")
    val commentList = when (rawComments) {
        is JsonArray -> rawComments
        is JsonObject -> (rawComments.getIgnoreCase("list") as? JsonArray)
            ?: (rawComments.getIgnoreCase("comments") as? JsonArray)
        else -> null
    } ?: JsonArray(emptyList())

    val comments = commentList
        .mapNotNull { parseNeteaseTrackComment(it as? JsonObject) }
        .distinctBy(TrackComment::id)
    val total = listOf(commentRoot, root)
        .firstNotNullOfOrNull { node -> node.firstLongOf("totalCount", "total", "commentCount") }
        ?.coerceAtLeast(comments.size.toLong())
        ?.toInt()
        ?: comments.size

    return ExtractedCommentPage(
        totalCount = total,
        comments = comments
    )
}

internal fun extractQqTrackComments(rawResponse: String, json: Json): ExtractedTrackComments? {
    val root = parseJsonObjectFromRaw(rawResponse, json) ?: return null
    val code = extractApiCode(root)
    if (code != null && code != 0 && code != 200) return null

    val commentRoot = (root.getIgnoreCase("comment") as? JsonObject)
        ?: (root.getIgnoreCase("data") as? JsonObject)
        ?: root

    val rawCommentList = commentRoot.getIgnoreCase("commentlist") ?: root.getIgnoreCase("commentlist")
    val commentList = when (rawCommentList) {
        is JsonArray -> rawCommentList
        is JsonObject -> (rawCommentList.getIgnoreCase("list") as? JsonArray)
            ?: (rawCommentList.getIgnoreCase("commentlist") as? JsonArray)
        else -> null
    } ?: JsonArray(emptyList())

    val comments = commentList.mapNotNull { item ->
        parseQqTrackComment(item as? JsonObject)
    }
    val total = listOf(commentRoot, root)
        .firstNotNullOfOrNull { node -> node.firstLongOf("commenttotal", "commentTotal", "total") }
        ?.coerceAtLeast(comments.size.toLong())
        ?.toInt()
        ?: comments.size

    return ExtractedTrackComments(
        totalCount = total,
        latestComments = comments
    )
}

private fun parseNeteaseTrackComment(comment: JsonObject?): TrackComment? {
    val item = comment ?: return null
    val content = item.firstStringOf("content").orEmpty().trim()
    if (content.isBlank()) return null

    val user = item.getIgnoreCase("user") as? JsonObject
    return TrackComment(
        id = item.firstStringOf("commentId", "commentid", "id").orEmpty().ifBlank { content.hashCode().toString() },
        authorName = user?.firstStringOf("nickname", "nickName", "nick").orEmpty().ifBlank { "匿名用户" },
        content = content,
        likedCount = (item.firstLongOf("likedCount", "likedcount", "praisenum") ?: 0L).toInt(),
        timeMs = normalizeCommentTime(item.firstLongOf("time", "timeStamp")),
        avatarUrl = user?.firstStringOf("avatarUrl", "avatarurl").orEmpty()
    )
}

private fun parseQqTrackComment(comment: JsonObject?): TrackComment? {
    val item = comment ?: return null
    val content = item.firstStringOf("rootcommentcontent", "content", "middlecommentcontent").orEmpty().trim()
    if (content.isBlank()) return null

    val user = (item.getIgnoreCase("userinfo") ?: item.getIgnoreCase("user")) as? JsonObject
    return TrackComment(
        id = item.firstStringOf("rootcommentid", "commentid", "id").orEmpty().ifBlank { content.hashCode().toString() },
        authorName = user?.firstStringOf("nick", "nickname", "nickName")
            .orEmpty()
            .ifBlank { item.firstStringOf("rootcommentnick", "nick", "nickname").orEmpty().ifBlank { "QQ用户" } },
        content = content,
        likedCount = (item.firstLongOf("praisenum", "likedCount", "praiseNum") ?: 0L).toInt(),
        timeMs = normalizeCommentTime(item.firstLongOf("time", "timeStamp", "commenttime")),
        avatarUrl = normalizeQqImageUrl(
            user?.firstStringOf("avatarurl", "avatarUrl", "avatar").orEmpty()
        )
    )
}

private fun parseJsonObjectFromRaw(rawResponse: String, json: Json): JsonObject? {
    val trimmed = rawResponse.trim()
    if (trimmed.isBlank()) return null

    val payload = when {
        trimmed.startsWith("{") -> trimmed
        trimmed.startsWith("[") -> trimmed
        else -> Regex(
            pattern = """^[^(]+\((.*)\)\s*;?$""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(trimmed)?.groupValues?.getOrNull(1)
    } ?: return null

    return runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
}

private fun normalizeCommentTime(rawTime: Long?): Long {
    val value = rawTime ?: return 0L
    return if (value in 1..99_999_999_999L) value * 1000 else value
}
