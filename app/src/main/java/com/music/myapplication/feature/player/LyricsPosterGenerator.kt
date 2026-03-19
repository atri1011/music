package com.music.myapplication.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.music.myapplication.core.common.normalizeCoverUrl
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class LyricsPosterTemplate(val displayName: String) {
    AURORA("流光"),
    PAPER("留白")
}

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
        val coverBitmap = loadCoverBitmap(
            context = context,
            coverUrl = track.coverUrl,
            sizePx = width / 3
        )

        when (template) {
            LyricsPosterTemplate.AURORA -> drawAuroraTemplate(canvas, track, lyricLine, coverBitmap)
            LyricsPosterTemplate.PAPER -> drawPaperTemplate(canvas, track, lyricLine, coverBitmap)
        }

        bitmap
    }

    private fun drawAuroraTemplate(
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

    private fun drawPaperTemplate(
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
            text = lyricLine.text.ifBlank { "把喜欢的歌词留下来。"},
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

    private fun drawStaticText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        left: Int,
        top: Int,
        maxWidth: Int,
        alignment: Layout.Alignment
    ): Int {
        val safeText = text.trim().ifBlank { return 0 }
        val layout = StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, maxWidth)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.1f)
            .build()
        canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun drawRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        rect: RectF,
        cornerRadius: Float
    ) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix()
        val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
        val dx = rect.left + (rect.width() - bitmap.width * scale) / 2f
        val dy = rect.top + (rect.height() - bitmap.height * scale) / 2f
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        shader.setLocalMatrix(matrix)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bitmapPaint)
    }

    private fun lyricTextSizeOf(text: String): Float {
        val length = text.trim().length
        return when {
            length >= 48 -> 46f
            length >= 28 -> 54f
            else -> 62f
        }
    }

    private suspend fun loadCoverBitmap(
        context: Context,
        coverUrl: String,
        sizePx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeCoverUrl(coverUrl)
        if (normalizedUrl.isBlank()) return@withContext null

        val imageLoader = ImageLoader.Builder(context).build()
        val request = ImageRequest.Builder(context)
            .data(normalizedUrl)
            .size(sizePx)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable ?: return@withContext null
        drawable.intrinsicWidth.takeIf { it > 0 } ?: return@withContext null
        drawable.intrinsicHeight.takeIf { it > 0 } ?: return@withContext null

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }

    private const val DEFAULT_WIDTH = 1080
    private const val DEFAULT_HEIGHT = 1920
}
