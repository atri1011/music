package com.music.myapplication.feature.player.actions

import android.content.Context
import android.graphics.Bitmap
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.player.LyricsPosterTemplate
import com.music.myapplication.feature.player.poster.LyricsPosterExporter

suspend fun saveLyricsPosterMessage(
    context: Context,
    bitmap: Bitmap,
    track: Track,
    template: LyricsPosterTemplate
): String = runCatching {
    LyricsPosterExporter.savePosterBitmap(
        context = context,
        bitmap = bitmap,
        track = track,
        template = template
    )
}.fold(
    onSuccess = { "已保存海报：$it" },
    onFailure = { it.message ?: "保存海报失败" }
)

suspend fun shareLyricsPosterErrorMessage(
    context: Context,
    bitmap: Bitmap,
    track: Track,
    template: LyricsPosterTemplate
): String? = runCatching {
    LyricsPosterExporter.sharePosterBitmap(
        context = context,
        bitmap = bitmap,
        track = track,
        template = template
    )
}.exceptionOrNull()?.let {
    it.message ?: "分享海报失败"
}