package com.music.myapplication.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.components.ShimmerMediaListItem
import com.music.myapplication.feature.player.PlayerViewModel

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    platform: String,
    title: String,
    source: String,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(playlistId, platform, source) {
        viewModel.loadPlaylist(playlistId, platform, title, source)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.error != null -> {
                // Back button + error
                Box(modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.loadPlaylist(playlistId, platform, title, source) }
                )
            }
            state.isLoading -> {
                // Header skeleton + shimmer list
                Box(modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(10) { ShimmerMediaListItem() }
                }
            }
            else -> {
                val headerCoverUrl = state.tracks.firstOrNull()?.coverUrl ?: ""
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Header with large cover + gradient
                    item(key = "header", contentType = "header") {
                        PlaylistHeader(
                            title = state.title.ifBlank { title },
                            coverUrl = headerCoverUrl,
                            trackCount = state.tracks.size,
                            onBack = onBack,
                            onPlayAll = {
                                if (state.tracks.isNotEmpty()) {
                                    playerViewModel.playTrack(state.tracks.first(), state.tracks, 0)
                                }
                            }
                        )
                    }

                    // Track list
                    itemsIndexed(
                        state.tracks,
                        key = { _, t -> "${t.platform.id}:${t.id}" },
                        contentType = { _, _ -> "track" }
                    ) { index, track ->
                        MediaListItem(
                            track = track,
                            index = index,
                            onClick = {
                                playerViewModel.playTrack(track, state.tracks, index)
                            },
                            onMoreClick = {
                                ShareUtils.shareTrack(context, track)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    coverUrl: String,
    trackCount: Int,
    onBack: () -> Unit,
    onPlayAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Background cover (blurred effect via low alpha + gradient)
        if (coverUrl.isNotEmpty()) {
            CoverImage(
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }

            // Center: cover + title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (coverUrl.isNotEmpty()) {
                    CoverImage(
                        url = coverUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Text(
                    text = "${trackCount}首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Play all button
            if (trackCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "播放全部",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
