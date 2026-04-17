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

internal fun drawAuroraLyricsPoster(
    canvas: Canvas,
    track: Track,
    lyricLine: LyricLine,
    coverBitmap: Bitmap?
) {
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            intArrayOf(
                Color.parseColor("#111827"),
                Color.parseColor("#3B0764"),
                Color.parseColor("#0F172A")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width, height, backgroundPaint)

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    glowPaint.color = Color.parseColor("#A855F7")
    glowPaint.alpha = 44
    canvas.drawCircle(width * 0.18f, height * 0.18f, width * 0.22f, glowPaint)
    glowPaint.color = Color.parseColor("#22D3EE")
    glowPaint.alpha = 34
    canvas.drawCircle(width * 0.82f, height * 0.72f, width * 0.28f, glowPaint)

    val cardRect = RectF(width * 0.08f, height * 0.08f, width * 0.92f, height * 0.92f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 24
    }
    canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        textSize = 28f
    }
    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = lyricTextSizeOf(lyricLine.text)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C4B5FD")
        textSize = 34f
    }
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 26f
    }

    coverBitmap?.let {
        drawRoundedBitmap(
            canvas = canvas,
            bitmap = it,
            rect = RectF(width * 0.12f, height * 0.12f, width * 0.34f, height * 0.245f),
            cornerRadius = 36f
        )
    }

    drawStaticText(
        canvas = canvas,
        text = track.title.ifBlank { "未知歌曲" },
        paint = titlePaint,
        left = (width * 0.40f).toInt(),
        top = (height * 0.125f).toInt(),
        maxWidth = (width * 0.44f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )
    drawStaticText(
        canvas = canvas,
        text = track.artist.ifBlank { track.platform.displayName },
        paint = subTitlePaint,
        left = (width * 0.40f).toInt(),
        top = (height * 0.185f).toInt(),
        maxWidth = (width * 0.44f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )

    val lyricTop = drawStaticText(
        canvas = canvas,
        text = "“${lyricLine.text.ifBlank { "此刻无词，只有旋律。" }}”",
        paint = lyricPaint,
        left = (width * 0.12f).toInt(),
        top = (height * 0.34f).toInt(),
        maxWidth = (width * 0.76f).toInt(),
        alignment = Layout.Alignment.ALIGN_CENTER
    )

    if (lyricLine.translation.isNotBlank()) {
        drawStaticText(
            canvas = canvas,
            text = lyricLine.translation,
            paint = translationPaint,
            left = (width * 0.16f).toInt(),
            top = (height * 0.34f).toInt() + lyricTop + 32,
            maxWidth = (width * 0.68f).toInt(),
            alignment = Layout.Alignment.ALIGN_CENTER
        )
    }

    drawStaticText(
        canvas = canvas,
        text = "${track.platform.displayName} · Music Player",
        paint = footerPaint,
        left = (width * 0.12f).toInt(),
        top = (height * 0.82f).toInt(),
        maxWidth = (width * 0.76f).toInt(),
        alignment = Layout.Alignment.ALIGN_CENTER
    )
}
