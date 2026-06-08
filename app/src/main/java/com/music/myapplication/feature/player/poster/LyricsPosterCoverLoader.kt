package com.music.myapplication.feature.player.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.music.myapplication.core.common.normalizeCoverUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun loadLyricsPosterCoverBitmap(
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

internal suspend fun loadLyricsPosterBackgroundBitmap(
    context: Context,
    backgroundUri: String?,
    width: Int,
    height: Int
): Bitmap? = withContext(Dispatchers.IO) {
    val uri = backgroundUri?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
    val imageLoader = ImageLoader.Builder(context).build()
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(width, height)
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
