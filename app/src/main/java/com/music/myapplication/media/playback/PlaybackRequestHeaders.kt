package com.music.myapplication.media.playback

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
    playableUrl.startsWith("http://", ignoreCase = true) ||
        playableUrl.startsWith("https://", ignoreCase = true)

private fun playbackRefererFor(playableUrl: String): String? {
    val normalizedUrl = playableUrl.lowercase()
    return when {
        "y.qq.com" in normalizedUrl || "qqmusic.qq.com" in normalizedUrl -> "https://y.qq.com/"
        "music.163.com" in normalizedUrl || "163cn.tv" in normalizedUrl -> "https://music.163.com/"
        "kuwo.cn" in normalizedUrl -> "https://www.kuwo.cn/"
        else -> null
    }
}

private fun Map<String, String>.containsHeader(name: String): Boolean =
    keys.any { key -> key.equals(name, ignoreCase = true) }
