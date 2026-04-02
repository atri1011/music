package com.music.myapplication.media.playback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val PLAYBACK_USER_AGENT = "MusicPlayer/1.0 Android"

internal fun buildPlaybackRequestHeaders(
    playableUrl: String,
    existingHeaders: Map<String, String> = emptyMap()
): Map<String, String> = buildMap {
    putAll(existingHeaders)
    if (!existingHeaders.containsHeader("User-Agent")) {
        put("User-Agent", PLAYBACK_USER_AGENT)
    }
    playbackRefererFor(playableUrl)?.let { referer ->
        if (!existingHeaders.containsHeader("Referer")) {
            put("Referer", referer)
        }
    }
}

internal fun shouldUsePlaybackCache(playableUrl: String): Boolean =
    parsePlaybackHttpUrl(playableUrl) != null

private fun playbackRefererFor(playableUrl: String): String? {
    val normalizedHost = parsePlaybackHttpUrl(playableUrl)?.host?.lowercase() ?: return null
    return when {
        normalizedHost.matchesPlaybackDomain("y.qq.com") ||
            normalizedHost.matchesPlaybackDomain("qqmusic.qq.com") -> "https://y.qq.com/"
        normalizedHost.matchesPlaybackDomain("music.163.com") ||
            normalizedHost.matchesPlaybackDomain("163cn.tv") -> "https://music.163.com/"
        normalizedHost.matchesPlaybackDomain("kuwo.cn") -> "https://www.kuwo.cn/"
        else -> null
    }
}

private fun Map<String, String>.containsHeader(name: String): Boolean =
    keys.any { key -> key.equals(name, ignoreCase = true) }

private fun parsePlaybackHttpUrl(playableUrl: String) =
    playableUrl
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.toHttpUrlOrNull()
        ?.takeIf { httpUrl ->
            httpUrl.scheme.equals("http", ignoreCase = true) ||
                httpUrl.scheme.equals("https", ignoreCase = true)
        }

private fun String.matchesPlaybackDomain(candidate: String): Boolean =
    equals(candidate, ignoreCase = true) || endsWith(".$candidate", ignoreCase = true)
