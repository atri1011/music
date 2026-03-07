package com.music.myapplication.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.music.myapplication.core.common.coverImageCacheKey
import com.music.myapplication.core.common.normalizeCoverUrl
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class DominantColorState(
    val dominantColor: Color = Color.Transparent,
    val onDominantColor: Color = Color.White,
    val mutedColor: Color = Color.Transparent,
    val isLoading: Boolean = true
)

private val dominantColorCache = ConcurrentHashMap<String, DominantColorState>()

@Stable
@Composable
fun rememberDominantColorState(
    coverUrl: String?,
    defaultColor: Color = Color(0xFF1C1C1E),
    defaultOnColor: Color = Color.White
): DominantColorState {
    val normalizedCoverUrl = remember(coverUrl) { normalizeCoverUrl(coverUrl.orEmpty()) }

    var state by remember(normalizedCoverUrl) {
        mutableStateOf(
            DominantColorState(
                dominantColor = defaultColor,
                onDominantColor = defaultOnColor,
                mutedColor = defaultColor,
                isLoading = normalizedCoverUrl.isNotBlank()
            )
        )
    }

    val context = LocalContext.current
    val imageLoader = context.imageLoader

    LaunchedEffect(normalizedCoverUrl) {
        if (normalizedCoverUrl.isBlank()) {
            state = DominantColorState(
                dominantColor = defaultColor,
                onDominantColor = defaultOnColor,
                mutedColor = defaultColor,
                isLoading = false
            )
            return@LaunchedEffect
        }

        val cached = dominantColorCache[normalizedCoverUrl]
        if (cached != null) {
            state = cached.copy(isLoading = false)
            return@LaunchedEffect
        }

        val bitmap = loadBitmap(
            context = context,
            imageLoader = imageLoader,
            url = normalizedCoverUrl,
            preferCacheOnly = true
        ) ?: loadBitmap(
            context = context,
            imageLoader = imageLoader,
            url = normalizedCoverUrl,
            preferCacheOnly = false
        )
        if (bitmap == null) {
            state = state.copy(isLoading = false)
            return@LaunchedEffect
        }

        val extracted = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).generate()
            val dominant = palette.getDarkVibrantColor(
                palette.getVibrantColor(
                    palette.getMutedColor(defaultColor.toArgbInt())
                )
            ).let { Color(it) }

            val muted = palette.getDarkMutedColor(
                palette.getMutedColor(defaultColor.toArgbInt())
            ).let { Color(it) }

            val onColor = if (dominant.luminance() > 0.5f) Color.Black else Color.White

            DominantColorState(
                dominantColor = dominant,
                onDominantColor = onColor,
                mutedColor = muted,
                isLoading = false
            )
        }
        dominantColorCache[normalizedCoverUrl] = extracted
        state = extracted
    }

    return state
}

private suspend fun loadBitmap(
    context: android.content.Context,
    imageLoader: ImageLoader,
    url: String,
    preferCacheOnly: Boolean
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val cacheKey = coverImageCacheKey(url)
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(128)
            .scale(Scale.FILL)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(
                if (preferCacheOnly) CachePolicy.READ_ONLY else CachePolicy.ENABLED
            )
            .apply {
                if (cacheKey.isNotEmpty()) {
                    memoryCacheKey(cacheKey)
                    diskCacheKey(cacheKey)
                }
            }
            .build()
        val result = imageLoader.execute(request)
        (result as? SuccessResult)?.drawable?.let { drawable ->
            (drawable as? BitmapDrawable)?.bitmap
        }
    } catch (_: Exception) {
        null
    }
}

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
