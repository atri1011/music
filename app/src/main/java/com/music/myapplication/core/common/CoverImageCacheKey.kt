package com.music.myapplication.core.common

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val COVER_CACHE_KEY_PREFIX = "cover:"
private const val DEFAULT_SCHEME = "https"

fun normalizeCoverUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""

    val urlWithScheme = if (trimmed.startsWith("//")) {
        "$DEFAULT_SCHEME:$trimmed"
    } else {
        trimmed
    }

    val parsed = urlWithScheme.toHttpUrlOrNull() ?: return urlWithScheme
    return parsed.newBuilder().build().toString()
}

fun coverImageCacheKey(rawUrl: String): String {
    val normalizedUrl = normalizeCoverUrl(rawUrl)
    if (normalizedUrl.isBlank()) return ""
    return "$COVER_CACHE_KEY_PREFIX$normalizedUrl"
}
