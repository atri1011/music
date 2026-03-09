package com.music.myapplication.core.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.music.myapplication.domain.model.Track

data class TrackShareOptions(
    val shareUrl: String? = null,
    val deepLink: String? = null
)

object ShareUtils {

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
}
