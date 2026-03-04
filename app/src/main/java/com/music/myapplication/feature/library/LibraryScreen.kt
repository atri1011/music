package com.music.myapplication.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToPlaylist: (id: String, name: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tabs = listOf("收藏", "最近播放", "歌单")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的音乐") },
                actions = {
                    IconButton(onClick = { viewModel.showApiKeyDialog(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "API Key 设置")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.selectedTab == 2) {
                FloatingActionButton(onClick = { viewModel.showCreateDialog(true) }) {
                    Icon(Icons.Default.Add, contentDescription = "新建歌单")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = state.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            when (state.selectedTab) {
                0 -> TrackList(
                    tracks = state.favorites,
                    emptyText = "还没有收藏歌曲",
                    onTrackClick = { track, index ->
                        playerViewModel.playTrack(track, state.favorites, index)
                    }
                )
                1 -> TrackList(
                    tracks = state.recentPlays,
                    emptyText = "还没有播放记录",
                    onTrackClick = { track, index ->
                        playerViewModel.playTrack(track, state.recentPlays, index)
                    }
                )
                2 -> PlaylistList(
                    playlists = state.playlists,
                    onPlaylistClick = { playlist -> onNavigateToPlaylist(playlist.id, playlist.name) },
                    onDelete = viewModel::deletePlaylist
                )
            }
        }

        if (state.showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.showCreateDialog(false) },
                onConfirm = viewModel::createPlaylist
            )
        }

        if (state.showApiKeyDialog) {
            ApiKeyDialog(
                initialValue = state.apiKey,
                onDismiss = { viewModel.showApiKeyDialog(false) },
                onConfirm = viewModel::saveApiKey
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<com.music.myapplication.domain.model.Track>,
    emptyText: String,
    onTrackClick: (com.music.myapplication.domain.model.Track, Int) -> Unit
) {
    if (tracks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, t -> "${t.platform.id}:${t.id}" }) { index, track ->
                MediaListItem(
                    track = track,
                    onClick = { onTrackClick(track, index) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<com.music.myapplication.domain.model.Playlist>,
    onPlaylistClick: (com.music.myapplication.domain.model.Playlist) -> Unit,
    onDelete: (String) -> Unit
) {
    if (playlists.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("还没有创建歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(playlists.size, key = { playlists[it].id }) { index ->
                val playlist = playlists[index]
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("${playlist.trackCount}首歌") },
                    trailingContent = {
                        IconButton(onClick = { onDelete(playlist.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ApiKeyDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var apiKey by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置 TuneHub API Key") },
        text = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("th_xxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(apiKey) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
