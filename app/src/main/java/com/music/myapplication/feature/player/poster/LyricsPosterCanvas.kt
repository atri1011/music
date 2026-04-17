package com.music.myapplication.feature.player.poster

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

internal fun drawStaticText(
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

internal fun drawRoundedBitmap(
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

internal fun lyricTextSizeOf(text: String): Float {
    val length = text.trim().length
    return when {
        length >= 48 -> 46f
        length >= 28 -> 54f
        else -> 62f
    }
}
