package com.music.myapplication.core.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.music.myapplication.domain.model.Track
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class TrackShareOptions(
    val shareUrl: String? = null,
    val deepLink: String? = null
)

object ShareUtils {

    private val shareUrlRegex =
        Regex("""https?://[^\s"'<>\\，。；：！？、】【《》、（）]+""", RegexOption.IGNORE_CASE)
    private val shareUrlLeadingTrimChars = setOf('(', '[', '{', '<', '《', '【', '"', '\'', '“')
    private val shareUrlTrailingTrimChars = setOf(
        ')', ']', '}', '>', '》', '】', '"', '\'', '”',
        '，', '。', '；', '：', '！', '？', '、',
        ',', '.', ';', ':', '!'
    )

    fun buildTrackShareSubject(track: Track): String {
        return listOf(track.title, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { "分享歌曲" }
    }

    fun buildTrackShareText(
        track: Track,
        options: TrackShareOptions = TrackShareOptions()
    ): String {
        val lines = buildList {
            add("歌曲：${track.title.ifBlank { "未知歌曲" }}")
            if (track.artist.isNotBlank()) add("歌手：${track.artist}")
            if (track.album.isNotBlank()) add("专辑：${track.album}")
            add("平台：${track.platform.displayName}")
            options.shareUrl?.takeIf { it.isNotBlank() }?.let { add("链接：$it") }
            options.deepLink?.takeIf { it.isNotBlank() }?.let { add("打开：$it") }
        }
        return lines.joinToString("\n")
    }

    fun extractShareUrlCandidates(rawText: String): List<String> {
        val normalizedInput = rawText.trim()
        if (normalizedInput.isBlank()) return emptyList()

        val textVariants = buildList {
            add(normalizedInput)

            val unescaped = normalizedInput
                .replace("\\/", "/")
                .replace("&amp;", "&")
            if (unescaped != normalizedInput) add(unescaped)

            decodeShareText(unescaped)
                ?.takeIf { it != unescaped }
                ?.let(::add)
        }

        val candidates = LinkedHashSet<String>()
        textVariants.forEach { variant ->
            shareUrlRegex.findAll(variant)
                .map { normalizeShareUrlCandidate(it.value) }
                .filter { it.isNotBlank() }
                .forEach(candidates::add)
        }
        return candidates.toList()
    }

    fun normalizeShareUrlCandidate(rawUrl: String): String {
        var candidate = rawUrl.trim()
            .replace("\\/", "/")
            .replace("&amp;", "&")

        while (candidate.isNotEmpty() && candidate.first() in shareUrlLeadingTrimChars) {
            candidate = candidate.drop(1)
        }
        while (candidate.isNotEmpty() && candidate.last() in shareUrlTrailingTrimChars) {
            candidate = candidate.dropLast(1)
        }
        return candidate
    }

    fun shareTrack(
        context: Context,
        track: Track,
        options: TrackShareOptions = TrackShareOptions(),
        chooserTitle: String = "分享歌曲"
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, buildTrackShareSubject(track))
            putExtra(Intent.EXTRA_TEXT, buildTrackShareText(track, options))
        }
        val chooserIntent = Intent.createChooser(intent, chooserTitle).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooserIntent)
    }

    private fun decodeShareText(rawText: String): String? {
        return runCatching {
            URLDecoder.decode(rawText, StandardCharsets.UTF_8.toString())
        }.getOrNull()
    }
}
