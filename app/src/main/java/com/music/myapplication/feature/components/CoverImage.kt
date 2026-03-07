package com.music.myapplication.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.music.myapplication.core.common.coverImageCacheKey
import com.music.myapplication.core.common.normalizeCoverUrl

@Composable
fun CoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showShimmer: Boolean = false
) {
    val context = LocalContext.current
    val normalizedUrl = remember(url) { normalizeCoverUrl(url) }
    val cacheKey = remember(normalizedUrl) { coverImageCacheKey(normalizedUrl) }
    var allowNetworkFallback by remember(normalizedUrl) { mutableStateOf(false) }

    val imageRequest = remember(normalizedUrl, cacheKey, allowNetworkFallback) {
        ImageRequest.Builder(context)
            .data(normalizedUrl.ifEmpty { null })
            .crossfade(200)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(
                if (allowNetworkFallback) CachePolicy.ENABLED else CachePolicy.READ_ONLY
            )
            .apply {
                if (cacheKey.isNotEmpty()) {
                    memoryCacheKey(cacheKey)
                    diskCacheKey(cacheKey)
                }
            }
            .build()
    }

    val painter = rememberAsyncImagePainter(
        model = imageRequest,
        onState = { state ->
            if (
                state is AsyncImagePainter.State.Error &&
                !allowNetworkFallback &&
                normalizedUrl.isNotEmpty()
            ) {
                allowNetworkFallback = true
            }
        }
    )

    val painterState = painter.state
    val isCacheOnlyMiss = painterState is AsyncImagePainter.State.Error &&
        !allowNetworkFallback &&
        normalizedUrl.isNotEmpty()
    val isFinalError = painterState is AsyncImagePainter.State.Error && !isCacheOnlyMiss

    Box(modifier = modifier) {
        when {
            isFinalError -> {
                CoverPlaceholder(modifier = Modifier.matchParentSize())
            }
            painterState is AsyncImagePainter.State.Empty ||
                painterState is AsyncImagePainter.State.Loading ||
                isCacheOnlyMiss -> {
                CoverPlaceholder(modifier = Modifier.matchParentSize())
                if (showShimmer) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .shimmerEffect(),
                        contentAlignment = Alignment.Center
                    ) {}
                }
            }
            painterState is AsyncImagePainter.State.Success -> Unit
        }

        if (!isFinalError) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale
            )
        }
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
    }
}
