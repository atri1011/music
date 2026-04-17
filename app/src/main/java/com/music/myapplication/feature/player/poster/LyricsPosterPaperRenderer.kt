package com.music.myapplication.feature.player.poster

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track

internal fun drawPaperLyricsPoster(
    canvas: Canvas,
    track: Track,
    lyricLine: LyricLine,
    coverBitmap: Bitmap?
) {
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()
    canvas.drawColor(Color.parseColor("#FAF7F0"))

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            intArrayOf(
                Color.parseColor("#FDE68A"),
                Color.parseColor("#FCA5A5")
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRoundRect(
        RectF(width * 0.08f, height * 0.12f, width * 0.92f, height * 0.86f),
        54f,
        54f,
        accentPaint
    )

    val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFDF8")
    }
    canvas.drawRoundRect(
        RectF(width * 0.11f, height * 0.15f, width * 0.89f, height * 0.83f),
        48f,
        48f,
        panelPaint
    )

    val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        textSize = 160f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    canvas.drawText("“", width * 0.16f, height * 0.29f, quotePaint)

    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        textSize = lyricTextSizeOf(lyricLine.text) - 4f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 30f
    }
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4B5563")
        textSize = 26f
    }

    val lyricHeight = drawStaticText(
        canvas = canvas,
        text = lyricLine.text.ifBlank { "把喜欢的歌词留下来。" },
        paint = lyricPaint,
        left = (width * 0.17f).toInt(),
        top = (height * 0.32f).toInt(),
        maxWidth = (width * 0.62f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )

    if (lyricLine.translation.isNotBlank()) {
        drawStaticText(
            canvas = canvas,
            text = lyricLine.translation,
            paint = translationPaint,
            left = (width * 0.17f).toInt(),
            top = (height * 0.32f).toInt() + lyricHeight + 28,
            maxWidth = (width * 0.62f).toInt(),
            alignment = Layout.Alignment.ALIGN_NORMAL
        )
    }

    coverBitmap?.let {
        drawRoundedBitmap(
            canvas = canvas,
            bitmap = it,
            rect = RectF(width * 0.62f, height * 0.62f, width * 0.82f, height * 0.76f),
            cornerRadius = 42f
        )
    }

    drawStaticText(
        canvas = canvas,
        text = track.title.ifBlank { "未知歌曲" },
        paint = titlePaint,
        left = (width * 0.17f).toInt(),
        top = (height * 0.69f).toInt(),
        maxWidth = (width * 0.40f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )
    drawStaticText(
        canvas = canvas,
        text = track.artist.ifBlank { track.platform.displayName },
        paint = artistPaint,
        left = (width * 0.17f).toInt(),
        top = (height * 0.74f).toInt(),
        maxWidth = (width * 0.40f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )

    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = 24f
    }
    drawStaticText(
        canvas = canvas,
        text = "${track.platform.displayName} · Music Player",
        paint = footerPaint,
        left = (width * 0.17f).toInt(),
        top = (height * 0.79f).toInt(),
        maxWidth = (width * 0.60f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )
}