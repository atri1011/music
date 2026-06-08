package com.music.myapplication.feature.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object MusicYearReportShareCard {
    suspend fun share(
        context: Context,
        state: MusicYearReportUiState
    ) = withContext(Dispatchers.IO) {
        val bitmap = createBitmap(state)
        val sharedDirectory = File(context.cacheDir, SHARED_IMAGE_DIRECTORY).apply { mkdirs() }
        val targetFile = File(sharedDirectory, buildFileName(state.year))
        targetFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = IMAGE_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${state.year} 音乐年度报告")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "分享音乐年度报告")
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun createBitmap(state: MusicYearReportUiState): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH.toFloat(),
                CARD_HEIGHT.toFloat(),
                intArrayOf(
                    AndroidColor.rgb(15, 30, 24),
                    AndroidColor.rgb(21, 45, 36),
                    AndroidColor.rgb(9, 13, 18)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), background)

        val titlePaint = textPaint(size = 58f, color = AndroidColor.WHITE, typeface = Typeface.BOLD)
        val subtitlePaint = textPaint(size = 28f, color = AndroidColor.argb(190, 255, 255, 255))
        val labelPaint = textPaint(size = 26f, color = AndroidColor.argb(175, 255, 255, 255))
        val valuePaint = textPaint(size = 42f, color = AndroidColor.WHITE, typeface = Typeface.BOLD)
        val smallValuePaint = textPaint(size = 32f, color = AndroidColor.WHITE, typeface = Typeface.BOLD)

        canvas.drawText("${state.year} 音乐年度报告", 72f, 132f, titlePaint)
        canvas.drawText("本地听歌旅程", 72f, 178f, subtitlePaint)

        canvas.drawRoundedPanel(72f, 236f, 936f, 310f, AndroidColor.argb(42, 255, 255, 255))
        canvas.drawMetric("聆听时长", formatShareDuration(state.totalListenDurationMs), 112f, 330f, labelPaint, valuePaint)
        canvas.drawMetric("播放次数", "${state.totalPlayCount} 次", 112f, 474f, labelPaint, valuePaint)
        canvas.drawMetric("活跃天数", "${state.activeDays} 天", 552f, 330f, labelPaint, valuePaint)
        canvas.drawMetric("常听时段", state.activeHour?.let { "${it} 点" } ?: "暂无", 552f, 474f, labelPaint, valuePaint)

        val topTrack = state.topTracks.firstOrNull()
        val topArtist = state.topArtists.firstOrNull()
        canvas.drawRoundedPanel(72f, 590f, 936f, 264f, AndroidColor.argb(34, 255, 255, 255))
        canvas.drawText("年度单曲", 112f, 660f, labelPaint)
        canvas.drawTextClipped(
            text = topTrack?.track?.title ?: "暂无",
            x = 112f,
            y = 728f,
            maxWidth = 790f,
            paint = valuePaint
        )
        canvas.drawTextClipped(
            text = topTrack?.track?.artist ?: "继续播放后会生成榜单",
            x = 112f,
            y = 780f,
            maxWidth = 790f,
            paint = subtitlePaint
        )

        canvas.drawRoundedPanel(72f, 904f, 936f, 252f, AndroidColor.argb(34, 255, 255, 255))
        canvas.drawText("年度歌手", 112f, 974f, labelPaint)
        canvas.drawTextClipped(
            text = topArtist?.name ?: "暂无",
            x = 112f,
            y = 1042f,
            maxWidth = 790f,
            paint = valuePaint
        )
        canvas.drawText(
            topArtist?.let { "${it.playCount} 次播放 · ${formatShareDuration(it.listenDurationMs)}" } ?: "播放记录越多，统计越准。",
            112f,
            1094f,
            subtitlePaint
        )

        canvas.drawRoundedPanel(72f, 1204f, 936f, 210f, AndroidColor.argb(34, 255, 255, 255))
        canvas.drawText("音乐日历", 112f, 1270f, labelPaint)
        canvas.drawCalendarStrip(state, startX = 112f, startY = 1312f, width = 856f, height = 54f)
        canvas.drawText("亮色越多，代表今年留下的播放足迹越密。", 112f, 1392f, subtitlePaint)

        canvas.drawText("Music App", 72f, 1518f, smallValuePaint)
        canvas.drawText("Generated locally", 72f, 1562f, labelPaint)
        return bitmap
    }

    private fun Canvas.drawMetric(
        label: String,
        value: String,
        x: Float,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        drawText(label, x, y, labelPaint)
        drawTextClipped(value, x, y + 62f, maxWidth = 360f, paint = valuePaint)
    }

    private fun Canvas.drawRoundedPanel(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        color: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        drawRoundRect(RectF(left, top, left + width, top + height), 34f, 34f, paint)
    }

    private fun Canvas.drawTextClipped(
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        var resolved = text.ifBlank { "暂无" }
        if (paint.measureText(resolved) <= maxWidth) {
            drawText(resolved, x, y, paint)
            return
        }
        val ellipsis = "..."
        while (resolved.length > 1 && paint.measureText(resolved + ellipsis) > maxWidth) {
            resolved = resolved.dropLast(1)
        }
        drawText(resolved + ellipsis, x, y, paint)
    }

    private fun Canvas.drawCalendarStrip(
        state: MusicYearReportUiState,
        startX: Float,
        startY: Float,
        width: Float,
        height: Float
    ) {
        val columns = 53
        val gap = 4f
        val cellWidth = (width - gap * (columns - 1)) / columns
        val cellHeight = height
        val dayCount = if (isLeapYear(state.year)) 366 else 365
        val buckets = IntArray(columns)
        state.calendarDays.forEach { stat ->
            val index = ((stat.dayOfYear - 1).coerceAtLeast(0) * columns / dayCount).coerceIn(0, columns - 1)
            buckets[index] += stat.playCount.coerceAtLeast(0)
        }
        val maxCount = buckets.maxOrNull()?.coerceAtLeast(1) ?: 1
        val quietPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(70, 255, 255, 255) }
        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (column in 0 until columns) {
            val bucketCount = buckets[column]
            val intensity = bucketCount.toFloat() / maxCount.toFloat()
            activePaint.color = if (bucketCount > 0) {
                AndroidColor.argb(
                    (90 + intensity.coerceIn(0f, 1f) * 165).toInt(),
                    49,
                    194,
                    124
                )
            } else {
                quietPaint.color
            }
            val x = startX + column * (cellWidth + gap)
            drawRoundRect(
                RectF(x, startY, x + cellWidth, startY + cellHeight),
                6f,
                6f,
                activePaint
            )
        }
    }

    private fun textPaint(
        size: Float,
        color: Int,
        typeface: Int = Typeface.NORMAL
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = Typeface.create(Typeface.SANS_SERIF, typeface)
    }

    private fun formatShareDuration(ms: Long): String {
        val totalMinutes = max(0L, ms / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0 -> "${hours} 小时 ${minutes} 分"
            minutes > 0 -> "${minutes} 分钟"
            ms > 0L -> "<1 分钟"
            else -> "0 分钟"
        }
    }

    private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    private fun buildFileName(year: Int): String = "music_year_report_${year}_${System.currentTimeMillis()}.png"

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1600
    private const val SHARED_IMAGE_DIRECTORY = "shared_images"
    private const val IMAGE_MIME_TYPE = "image/png"
}

suspend fun shareMusicYearReportMessage(
    context: Context,
    state: MusicYearReportUiState
): String? = runCatching {
    MusicYearReportShareCard.share(context, state)
}.exceptionOrNull()?.let { error ->
    error.message ?: "分享年度报告失败"
}
