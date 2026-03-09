package com.music.myapplication.feature.album

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.components.ShimmerMediaListItem
import com.music.myapplication.feature.player.PlayerViewModel

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                repeat(8) { ShimmerMediaListItem() }
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                ErrorView(message = state.error!!, onRetry = viewModel::retry)
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        CoverImage(
                            url = state.coverUrl,
                            contentDescription = state.albumName,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = state.albumName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (state.artistName.isNotBlank()) {
                            Text(
                                text = state.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (state.tracks.isNotEmpty()) {
                            Text(
                                text = "${state.tracks.size}首歌曲",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.tracks.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    playerViewModel.playTrack(
                                        state.tracks.first(),
                                        state.tracks,
                                        0
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("全部播放")
                            }
                        }
                    }
                }

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
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
