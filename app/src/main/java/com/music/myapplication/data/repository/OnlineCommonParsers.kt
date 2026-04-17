package com.music.myapplication.data.repository

import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.domain.model.SearchResultItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun JsonObject.getIgnoreCase(key: String): JsonElement? {
    return this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

internal fun JsonObject.firstStringOf(vararg keys: String): String? {
    keys.forEach { key ->
        val value = (getIgnoreCase(key) as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}

internal fun JsonObject.firstLongOf(vararg keys: String): Long? {
    keys.forEach { key ->
        val value = (getIgnoreCase(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        if (value != null) return value
    }
    return null
}

internal fun extractNestedArray(node: JsonElement?, vararg preferredKeys: String): JsonArray? {
    return when (node) {
        is JsonArray -> node
        is JsonObject -> {
            preferredKeys.firstNotNullOfOrNull { key ->
                extractNestedArray(node.getIgnoreCase(key), *preferredKeys)
            }
        }

        else -> null
    }
}

internal fun extractJoinedNames(node: JsonElement?): String {
    return when (node) {
        is JsonArray -> node.mapNotNull { item ->
            when (item) {
                is JsonObject -> item.firstStringOf("name", "artistName", "singerName", "title")
                is JsonPrimitive -> item.contentOrNull
                else -> null
            }?.trim()?.takeIf(String::isNotBlank)
        }.distinct().joinToString("/")

        is JsonObject -> node.firstStringOf("name", "artistName", "singerName", "title").orEmpty()
        is JsonPrimitive -> node.contentOrNull.orEmpty()
        else -> ""
    }
}

internal fun extractTextList(node: JsonElement?, vararg objectKeys: String): List<String> {
    val candidateKeys = if (objectKeys.isEmpty()) {
        listOf("name", "title", "label", "text")
    } else {
        objectKeys.toList()
    }

    return when (node) {
        is JsonArray -> node.flatMap { child ->
            extractTextList(child, *candidateKeys.toTypedArray())
        }

        is JsonObject -> candidateKeys.firstNotNullOfOrNull { key ->
            node.firstStringOf(key)
        }?.let(::listOf).orEmpty()

        is JsonPrimitive -> node.contentOrNull
            ?.split(',', '，', '、')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

        else -> emptyList()
    }.distinct()
}

internal fun Long?.toSearchCount(): Int {
    val value = this ?: return 0
    return value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun String.isLikelySameTitle(other: String): Boolean {
    val left = normalizeComparisonText()
    val right = other.normalizeComparisonText()
    if (left.isBlank() || right.isBlank()) return true
    return left == right || left.contains(right) || right.contains(left)
}

internal fun String.isLikelySameArtist(other: String): Boolean {
    if (isBlank() || other.isBlank()) return true
    val left = normalizeComparisonText()
    val right = other.normalizeComparisonText()
    if (left.contains(right) || right.contains(left)) return true

    val leftTokens = left.split(Regex("[,，/&、|]")).map(String::trim).filter(String::isNotBlank)
    val rightTokens = right.split(Regex("[,，/&、|]")).map(String::trim).filter(String::isNotBlank)
    return leftTokens.any { token ->
        rightTokens.any { otherToken ->
            token == otherToken || token.contains(otherToken) || otherToken.contains(token)
        }
    }
}

internal fun String.normalizeComparisonText(): String {
    return lowercase()
        .replace(" ", "")
        .replace(Regex("""[\(\)（）\[\]【】《》\-—_·•.,，:：'"!?！？]"""), "")
}

internal fun extractApiCode(data: JsonElement?): Int? {
    val root = data as? JsonObject ?: return null
    return (root.getIgnoreCase("code") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}

internal fun extractApiMessage(data: JsonElement?): String {
    val root = data as? JsonObject ?: return ""
    return root.firstStringOf("message", "msg").orEmpty()
}

internal fun shareRefererFor(url: String): String? {
    val normalizedUrl = url.lowercase()
    return when {
        "y.qq.com" in normalizedUrl -> "https://y.qq.com/"
        "music.163.com" in normalizedUrl ||
            "music.126.net" in normalizedUrl ||
            "vod.126.net" in normalizedUrl ||
            "163cn.tv" in normalizedUrl -> "https://music.163.com/"
        "kuwo.cn" in normalizedUrl -> "https://www.kuwo.cn/"
        else -> null
    }
}

internal fun extractShareTarget(rawContent: String): String? {
    if (rawContent.isBlank()) return null

    val variants = buildList {
        add(rawContent)

        val unescaped = rawContent
            .replace("\\/", "/")
            .replace("&amp;", "&")
        if (unescaped != rawContent) add(unescaped)

        val decoded = runCatching {
            java.net.URLDecoder.decode(unescaped, java.nio.charset.StandardCharsets.UTF_8.toString())
        }.getOrNull()
        decoded?.takeIf { it != unescaped }?.let(::add)
    }

    val prioritizedUrls = variants.asSequence()
        .flatMap { ShareUtils.extractShareUrlCandidates(it).asSequence() }
        .filter(::isSupportedShareUrl)
        .distinct()
        .sortedByDescending(::shareTargetPriority)
        .toList()
    prioritizedUrls.firstOrNull()?.let { return it }

    return variants.asSequence()
        .mapNotNull { candidate ->
            Regex(
                """(?:playlist(?:_id)?|listid|id|disstid|pid)\s*[:=]\s*["']?(\d{5,})""",
                RegexOption.IGNORE_CASE
            ).find(candidate)?.groupValues?.getOrNull(1)
        }
        .firstOrNull()
}

private fun isSupportedShareUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return "y.qq.com" in normalized ||
        "music.163.com" in normalized ||
        "163cn.tv" in normalized ||
        "kuwo.cn" in normalized
}

private fun shareTargetPriority(url: String): Int {
    val normalized = url.lowercase()
    return when {
        Regex("""(?:playlist(?:_detail)?/\d+|[?&](?:id|disstid|pid)=\d+)""").containsMatchIn(normalized) -> 3
        "163cn.tv" in normalized -> 2
        else -> 1
    }
}

internal fun normalizeVideoLikeUrl(rawUrl: String): String {
    if (!rawUrl.startsWith("http://", ignoreCase = true)) return rawUrl

    val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
    val host = parsed.host.lowercase()
    val shouldUpgradeToHttps = host == "wx.music.tc.qq.com" ||
        host.endsWith(".music.tc.qq.com") ||
        host.endsWith(".qqmusic.qq.com")

    return if (shouldUpgradeToHttps) {
        parsed.newBuilder().scheme("https").build().toString()
    } else {
        rawUrl
    }
}

internal fun isVideoLikeKey(key: String): Boolean {
    val normalized = key.lowercase()
    return normalized.contains("mv") ||
        normalized.contains("video") ||
        normalized.contains("mp4") ||
        normalized.contains("m3u8")
}

internal fun isLikelyVideoLikeUrl(url: String): Boolean {
    val normalized = url.lowercase()
    return normalized.contains(".mp4") ||
        normalized.contains(".m3u8") ||
        normalized.contains(".webm") ||
        normalized.contains("mime=video") ||
        normalized.contains("type=mp4") ||
        normalized.contains("format=mp4")
}

internal fun extractFirstVideoLikeUrl(element: JsonElement?): String? {
    return when (element) {
        is JsonObject -> {
            element.entries.firstNotNullOfOrNull { (key, value) ->
                when (value) {
                    is JsonPrimitive -> {
                        value.contentOrNull
                            ?.takeIf { it.startsWith("http", ignoreCase = true) }
                            ?.takeIf { isVideoLikeKey(key) || isLikelyVideoLikeUrl(it) }
                    }

                    else -> extractFirstVideoLikeUrl(value)
                }
            }
        }

        is JsonArray -> element.firstNotNullOfOrNull(::extractFirstVideoLikeUrl)
        is JsonPrimitive -> {
            element.contentOrNull
                ?.takeIf { it.startsWith("http", ignoreCase = true) && isLikelyVideoLikeUrl(it) }
        }

        else -> null
    }
}
