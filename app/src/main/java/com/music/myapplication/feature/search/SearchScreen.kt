package com.music.myapplication.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.LoadingView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.components.PlatformFilterChips
import com.music.myapplication.feature.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val state by searchViewModel.state.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= state.tracks.size - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.tracks.isNotEmpty()) {
            searchViewModel.loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = state.query,
                    onQueryChange = searchViewModel::onQueryChange,
                    onSearch = { searchViewModel.submitSearch() },
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("搜索歌曲、歌手") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { searchViewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    }
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {}

        PlatformFilterChips(
            selectedPlatform = state.platform,
            onPlatformSelected = searchViewModel::onPlatformChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            state.error != null && state.tracks.isEmpty() -> {
                ErrorView(message = state.error!!, onRetry = searchViewModel::retry)
            }
            state.isLoading && state.tracks.isEmpty() -> {
                LoadingView()
            }
            state.tracks.isEmpty() && state.query.isBlank() -> {
                // Empty state
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "输入关键词开始搜索",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.tracks.isEmpty() && state.query.isNotBlank() && !state.isLoading -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "未找到相关歌曲",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.tracks, key = { _, t -> "${t.platform.id}:${t.id}" }) { index, track ->
                        MediaListItem(
                            track = track,
                            index = index,
                            onClick = {
                                playerViewModel.playTrack(track, state.tracks, index)
                            }
                        )
                    }
                    if (state.isLoading) {
                        item {
                            LoadingView(modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}
