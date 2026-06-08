package com.music.myapplication.core.common

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val NOTIFICATION_ARTWORK_SIZE = 800

fun normalizeNotificationArtworkUrl(rawUrl: String): String {
    val normalizedUrl = normalizeCoverUrl(rawUrl)
    if (normalizedUrl.isBlank()) return ""

    val qqSizedUrl = normalizedUrl.replace(
        Regex("T00([12])R\\d+x\\d+M000"),
        "T00$1R${NOTIFICATION_ARTWORK_SIZE}x${NOTIFICATION_ARTWORK_SIZE}M000"
    )
    if (qqSizedUrl != normalizedUrl) return qqSizedUrl

    val neteaseSizedUrl = withNeteaseArtworkSize(normalizedUrl)
    if (neteaseSizedUrl != normalizedUrl) return neteaseSizedUrl

    return normalizedUrl.replace(
        Regex("/(\\d{2,4})x(\\d{2,4})/"),
        "/${NOTIFICATION_ARTWORK_SIZE}x${NOTIFICATION_ARTWORK_SIZE}/"
    )
}

private fun withNeteaseArtworkSize(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    val host = parsed.host.lowercase()
    if (!host.contains("music.126.net") && !host.contains("music.163.com")) return url

    return parsed.newBuilder()
        .setQueryParameter("param", "${NOTIFICATION_ARTWORK_SIZE}y$NOTIFICATION_ARTWORK_SIZE")
        .build()
        .toString()
}
