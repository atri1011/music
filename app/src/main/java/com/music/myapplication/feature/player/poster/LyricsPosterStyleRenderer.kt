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

internal fun drawStyledLyricsPoster(
    canvas: Canvas,
    track: Track,
    lyricLine: LyricLine,
    coverBitmap: Bitmap?,
    customBackgroundBitmap: Bitmap?,
    template: LyricsPosterTemplate
) {
    val style = posterStyleFor(template)
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()

    if (customBackgroundBitmap != null && template == LyricsPosterTemplate.CUSTOM) {
        drawRoundedBitmap(
            canvas = canvas,
            bitmap = customBackgroundBitmap,
            rect = RectF(0f, 0f, width, height),
            cornerRadius = 0f
        )
        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.overlayColor
                alpha = 132
            }
        )
    } else {
        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width,
                    height,
                    intArrayOf(style.backgroundStart, style.backgroundMiddle, style.backgroundEnd),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
    }

    drawPosterAccents(canvas, style)

    val panelRect = RectF(width * 0.09f, height * 0.10f, width * 0.91f, height * 0.88f)
    canvas.drawRoundRect(
        panelRect,
        style.panelRadius,
        style.panelRadius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.panelColor
            alpha = style.panelAlpha
        }
    )

    coverBitmap?.let { bitmap ->
        drawRoundedBitmap(
            canvas = canvas,
            bitmap = bitmap,
            rect = RectF(width * 0.15f, height * 0.135f, width * 0.35f, height * 0.247f),
            cornerRadius = if (style.roundCover) 56f else 18f
        )
    }

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.titleColor
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.subtitleColor
        textSize = 27f
    }
    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.lyricColor
        textSize = lyricTextSizeOf(lyricLine.text) + style.lyricSizeOffset
        typeface = Typeface.create(if (style.serifLyric) Typeface.SERIF else Typeface.DEFAULT, Typeface.BOLD)
    }
    val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.subtitleColor
        textSize = 30f
    }
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.footerColor
        textSize = 24f
    }

    drawStaticText(
        canvas = canvas,
        text = track.title.ifBlank { "未知歌曲" },
        paint = titlePaint,
        left = (width * 0.40f).toInt(),
        top = (height * 0.145f).toInt(),
        maxWidth = (width * 0.44f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )
    drawStaticText(
        canvas = canvas,
        text = track.artist.ifBlank { track.platform.displayName },
        paint = artistPaint,
        left = (width * 0.40f).toInt(),
        top = (height * 0.195f).toInt(),
        maxWidth = (width * 0.44f).toInt(),
        alignment = Layout.Alignment.ALIGN_NORMAL
    )

    val lyricText = lyricLine.text.ifBlank { "此刻无词，只有旋律。" }
    val lyricHeight = drawStaticText(
        canvas = canvas,
        text = if (style.quoteLyric) "“$lyricText”" else lyricText,
        paint = lyricPaint,
        left = (width * 0.15f).toInt(),
        top = (height * 0.36f).toInt(),
        maxWidth = (width * 0.70f).toInt(),
        alignment = if (style.centerText) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
    )

    if (lyricLine.translation.isNotBlank()) {
        drawStaticText(
            canvas = canvas,
            text = lyricLine.translation,
            paint = translationPaint,
            left = (width * 0.18f).toInt(),
            top = (height * 0.36f).toInt() + lyricHeight + 30,
            maxWidth = (width * 0.64f).toInt(),
            alignment = if (style.centerText) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        )
    }

    canvas.drawRoundRect(
        RectF(width * 0.15f, height * 0.78f, width * 0.85f, height * 0.785f),
        3f,
        3f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.accentColor }
    )
    drawStaticText(
        canvas = canvas,
        text = "${track.platform.displayName} · Music Player",
        paint = footerPaint,
        left = (width * 0.15f).toInt(),
        top = (height * 0.81f).toInt(),
        maxWidth = (width * 0.70f).toInt(),
        alignment = Layout.Alignment.ALIGN_CENTER
    )
}

private fun drawPosterAccents(canvas: Canvas, style: PosterStyle) {
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.accentColor }
    paint.alpha = 54
    canvas.drawCircle(width * 0.18f, height * 0.22f, width * style.firstGlowScale, paint)
    paint.alpha = 42
    canvas.drawCircle(width * 0.82f, height * 0.72f, width * style.secondGlowScale, paint)
    paint.alpha = 210
    canvas.drawRoundRect(
        RectF(width * 0.14f, height * 0.30f, width * 0.26f, height * 0.306f),
        3f,
        3f,
        paint
    )
}

private data class PosterStyle(
    val backgroundStart: Int,
    val backgroundMiddle: Int,
    val backgroundEnd: Int,
    val overlayColor: Int,
    val panelColor: Int,
    val panelAlpha: Int,
    val accentColor: Int,
    val titleColor: Int,
    val lyricColor: Int,
    val subtitleColor: Int,
    val footerColor: Int,
    val panelRadius: Float = 52f,
    val lyricSizeOffset: Float = 0f,
    val serifLyric: Boolean = false,
    val centerText: Boolean = true,
    val quoteLyric: Boolean = true,
    val roundCover: Boolean = false,
    val firstGlowScale: Float = 0.22f,
    val secondGlowScale: Float = 0.26f
)

private fun posterStyleFor(template: LyricsPosterTemplate): PosterStyle = when (template) {
    LyricsPosterTemplate.VINYL -> PosterStyle(
        backgroundStart = color("#111111"), backgroundMiddle = color("#242424"), backgroundEnd = color("#050505"),
        overlayColor = color("#000000"), panelColor = Color.WHITE, panelAlpha = 20,
        accentColor = color("#F5F5F5"), titleColor = Color.WHITE, lyricColor = Color.WHITE,
        subtitleColor = color("#D6D6D6"), footerColor = color("#BDBDBD"), roundCover = true
    )
    LyricsPosterTemplate.MIDNIGHT -> PosterStyle(
        backgroundStart = color("#020617"), backgroundMiddle = color("#0F172A"), backgroundEnd = color("#1E1B4B"),
        overlayColor = color("#020617"), panelColor = Color.WHITE, panelAlpha = 22,
        accentColor = color("#38BDF8"), titleColor = Color.WHITE, lyricColor = Color.WHITE,
        subtitleColor = color("#BAE6FD"), footerColor = color("#CBD5E1")
    )
    LyricsPosterTemplate.SUNSET -> PosterStyle(
        backgroundStart = color("#7C2D12"), backgroundMiddle = color("#FB923C"), backgroundEnd = color("#581C87"),
        overlayColor = color("#431407"), panelColor = Color.WHITE, panelAlpha = 34,
        accentColor = color("#FED7AA"), titleColor = Color.WHITE, lyricColor = Color.WHITE,
        subtitleColor = color("#FFEDD5"), footerColor = color("#FFF7ED"), serifLyric = true
    )
    LyricsPosterTemplate.NEON -> PosterStyle(
        backgroundStart = color("#0A0A0A"), backgroundMiddle = color("#312E81"), backgroundEnd = color("#171717"),
        overlayColor = color("#000000"), panelColor = color("#0F172A"), panelAlpha = 170,
        accentColor = color("#22D3EE"), titleColor = Color.WHITE, lyricColor = color("#F0FDFA"),
        subtitleColor = color("#A5F3FC"), footerColor = color("#67E8F9"), lyricSizeOffset = 2f
    )
    LyricsPosterTemplate.MONO -> PosterStyle(
        backgroundStart = color("#F8FAFC"), backgroundMiddle = color("#E5E7EB"), backgroundEnd = color("#CBD5E1"),
        overlayColor = color("#111827"), panelColor = Color.WHITE, panelAlpha = 220,
        accentColor = color("#111827"), titleColor = color("#111827"), lyricColor = color("#111827"),
        subtitleColor = color("#4B5563"), footerColor = color("#6B7280"), centerText = false, quoteLyric = false
    )
    LyricsPosterTemplate.FILM -> PosterStyle(
        backgroundStart = color("#292524"), backgroundMiddle = color("#78350F"), backgroundEnd = color("#1C1917"),
        overlayColor = color("#1C1917"), panelColor = color("#FEF3C7"), panelAlpha = 210,
        accentColor = color("#F59E0B"), titleColor = color("#1C1917"), lyricColor = color("#1C1917"),
        subtitleColor = color("#57534E"), footerColor = color("#78716C"), serifLyric = true, centerText = false
    )
    LyricsPosterTemplate.OCEAN -> PosterStyle(
        backgroundStart = color("#0F766E"), backgroundMiddle = color("#0891B2"), backgroundEnd = color("#082F49"),
        overlayColor = color("#042F2E"), panelColor = Color.WHITE, panelAlpha = 32,
        accentColor = color("#99F6E4"), titleColor = Color.WHITE, lyricColor = Color.WHITE,
        subtitleColor = color("#CCFBF1"), footerColor = color("#E0F2FE")
    )
    LyricsPosterTemplate.FOREST -> PosterStyle(
        backgroundStart = color("#052E16"), backgroundMiddle = color("#166534"), backgroundEnd = color("#1C1917"),
        overlayColor = color("#052E16"), panelColor = color("#F0FDF4"), panelAlpha = 206,
        accentColor = color("#86EFAC"), titleColor = color("#052E16"), lyricColor = color("#052E16"),
        subtitleColor = color("#166534"), footerColor = color("#15803D"), centerText = false, roundCover = true
    )
    LyricsPosterTemplate.CLASSIC -> PosterStyle(
        backgroundStart = color("#1F2937"), backgroundMiddle = color("#4B5563"), backgroundEnd = color("#111827"),
        overlayColor = color("#111827"), panelColor = color("#F9FAFB"), panelAlpha = 232,
        accentColor = color("#B45309"), titleColor = color("#111827"), lyricColor = color("#111827"),
        subtitleColor = color("#4B5563"), footerColor = color("#6B7280"), serifLyric = true
    )
    LyricsPosterTemplate.MINIMAL -> PosterStyle(
        backgroundStart = color("#FFFFFF"), backgroundMiddle = color("#F8FAFC"), backgroundEnd = color("#E2E8F0"),
        overlayColor = color("#111827"), panelColor = Color.WHITE, panelAlpha = 244,
        accentColor = color("#10B981"), titleColor = color("#111827"), lyricColor = color("#111827"),
        subtitleColor = color("#64748B"), footerColor = color("#94A3B8"), centerText = false, quoteLyric = false
    )
    LyricsPosterTemplate.CUSTOM -> PosterStyle(
        backgroundStart = color("#111827"), backgroundMiddle = color("#374151"), backgroundEnd = color("#030712"),
        overlayColor = color("#000000"), panelColor = Color.WHITE, panelAlpha = 26,
        accentColor = color("#FFFFFF"), titleColor = Color.WHITE, lyricColor = Color.WHITE,
        subtitleColor = color("#E5E7EB"), footerColor = color("#D1D5DB"), lyricSizeOffset = 4f
    )
    LyricsPosterTemplate.AURORA,
    LyricsPosterTemplate.PAPER -> PosterStyle(
        backgroundStart = color("#111827"), backgroundMiddle = color("#3B0764"), backgroundEnd = color("#0F172A"),
        overlayColor = color("#000000"), panelColor = Color.WHITE, panelAlpha = 24,
        accentColor = color("#A855F7"), titleColor = Color.WHITE, lyricColor = Color.WHITE,
        subtitleColor = color("#D1D5DB"), footerColor = color("#CBD5E1")
    )
}

private fun color(value: String): Int = Color.parseColor(value)
