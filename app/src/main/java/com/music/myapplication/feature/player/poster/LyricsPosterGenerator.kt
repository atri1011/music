package com.music.myapplication.feature.player.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LyricsPosterGenerator {

    suspend fun generate(
        context: Context,
        track: Track,
        lyricLine: LyricLine,
        template: LyricsPosterTemplate,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val coverBitmap = loadLyricsPosterCoverBitmap(
            context = context,
            coverUrl = track.coverUrl,
            sizePx = width / 3
        )

        when (template) {
            LyricsPosterTemplate.AURORA -> drawAuroraLyricsPoster(canvas, track, lyricLine, coverBitmap)
            LyricsPosterTemplate.PAPER -> drawPaperLyricsPoster(canvas, track, lyricLine, coverBitmap)
        }

        bitmap
    }

    private const val DEFAULT_WIDTH = 1080
    private const val DEFAULT_HEIGHT = 1920
}
