package com.music.myapplication.feature.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.LoadingView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    platform: String,
    title: String,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId, platform, title)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.title.ifBlank { title }) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (state.tracks.isNotEmpty()) {
                    TextButton(onClick = {
                        playerViewModel.playTrack(state.tracks.first(), state.tracks, 0)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("播放全部", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        )

        when {
            state.error != null -> {
                ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.loadPlaylist(playlistId, platform, title) }
                )
            }
            state.isLoading -> LoadingView()
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                }
            }
        }
    }
}
