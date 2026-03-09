package com.music.myapplication.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.SuggestionType
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.MediaListItem
import com.music.myapplication.feature.components.PlatformFilterChips
import com.music.myapplication.feature.components.ShimmerMediaListItem
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.glassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: ((String, String, String) -> Unit)? = null,
    onNavigateToAlbum: ((String, String, String, String, String) -> Unit)? = null,
    onNavigateToPlaylist: ((String, String, String) -> Unit)? = null
) {
    val state by searchViewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val resultCount = if (state.searchType == SearchType.SONG) state.tracks.size else state.genericResults.size

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= resultCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && resultCount > 0) {
            searchViewModel.loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = searchViewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("搜索歌曲、歌手、专辑、歌单") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { searchViewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus(force = true)
                    searchViewModel.submitSearch()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        PlatformFilterChips(
            selectedPlatform = state.platform,
            onPlatformSelected = { platform ->
                focusManager.clearFocus(force = true)
                searchViewModel.onPlatformChange(platform)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SearchTypeTabRow(
            selectedType = state.searchType,
            onTypeSelected = { type ->
                focusManager.clearFocus(force = true)
                searchViewModel.onSearchTypeChange(type)
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // Suggestion overlay
            if (state.showSuggestions && state.suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f)
                        .padding(horizontal = 16.dp)
                        .glassSurface(RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    state.suggestions.take(8).forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchViewModel.onSuggestionClick(suggestion) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (suggestion.type) {
                                    SuggestionType.SONG -> Icons.Outlined.MusicNote
                                    SuggestionType.ARTIST -> Icons.AutoMirrored.Outlined.TrendingUp
                                    else -> Icons.Default.Search
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = suggestion.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Main content
            when {
                state.error != null && resultCount == 0 -> {
                    ErrorView(message = state.error!!, onRetry = searchViewModel::retry)
                }
                state.isLoading && resultCount == 0 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(8) { ShimmerMediaListItem() }
                    }
                }
                resultCount == 0 && state.query.isBlank() -> {
                    // Hot search + history (only shown for SONG type or when no query)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Search History
                        if (state.searchHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "搜索历史",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = searchViewModel::clearHistory) {
                                    Text("清空", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.searchHistory.forEach { keyword ->
                                    AssistChip(
                                        onClick = { searchViewModel.onHistoryClick(keyword) },
                                        label = { Text(keyword, maxLines = 1) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "删除",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { searchViewModel.removeHistoryItem(keyword) }
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Hot Search
                        if (state.hotKeywords.isNotEmpty()) {
                            Text(
                                text = "热门搜索",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            state.hotKeywords.take(20).forEachIndexed { index, keyword ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { searchViewModel.onHotKeywordClick(keyword) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
                                        color = if (index < 3) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    if (index < 3) {
                                        Icon(
                                            Icons.Outlined.LocalFireDepartment,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (index < 3) FontWeight.Medium else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else if (!state.isHotLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = "输入关键词开始搜索",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                resultCount == 0 && state.query.isNotBlank() && !state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到相关${state.searchType.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    if (state.searchType == SearchType.SONG) {
                        // Song results
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                                    onArtistClick = if (track.platform != Platform.KUWO) {
                                        onNavigateToArtist?.let { nav ->
                                            { t -> nav(t.id, t.platform.id, t.artist) }
                                        }
                                    } else null
                                )
                            }
                            if (state.isLoading) {
                                item(contentType = "loading") {
                                    ShimmerMediaListItem(modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    } else {
                        // Generic results (artist/album/playlist)
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(
                                state.genericResults,
                                key = { "${it.platform.id}:${it.type.name}:${it.id}" },
                                contentType = { it.type.name }
                            ) { item ->
                                SearchResultListItem(
                                    item = item,
                                    onClick = {
                                        when (item.type) {
                                            SearchType.ARTIST -> {
                                                onNavigateToArtist?.invoke(item.id, item.platform.id, item.title)
                                            }
                                            SearchType.ALBUM -> {
                                                onNavigateToAlbum?.invoke(item.id, item.platform.id, item.title, item.subtitle, item.coverUrl)
                                            }
                                            SearchType.PLAYLIST -> {
                                                onNavigateToPlaylist?.invoke(item.id, item.platform.id, item.title)
                                            }
                                            else -> {}
                                        }
                                    }
                                )
                            }
                            if (state.isLoading) {
                                item(contentType = "loading") {
                                    ShimmerMediaListItem(modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTypeTabRow(
    selectedType: SearchType,
    onTypeSelected: (SearchType) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = SearchType.entries
    val selectedIndex = types.indexOf(selectedType)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        divider = {}
    ) {
        types.forEach { type ->
            Tab(
                selected = type == selectedType,
                onClick = { onTypeSelected(type) },
                text = {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun SearchResultListItem(
    item: SearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverSize = if (item.type == SearchType.ARTIST) 52.dp else 56.dp
    val coverShape = if (item.type == SearchType.ARTIST) CircleShape else RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = item.coverUrl,
            contentDescription = item.title,
            modifier = Modifier
                .size(coverSize)
                .clip(coverShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitleText = buildSubtitle(item)
            if (subtitleText.isNotBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

private fun buildSubtitle(item: SearchResultItem): String {
    val parts = mutableListOf<String>()
    when (item.type) {
        SearchType.ARTIST -> {
            if (item.trackCount > 0) parts.add("${item.trackCount}首歌曲")
            parts.add(item.platform.displayName)
        }
        SearchType.ALBUM -> {
            if (item.subtitle.isNotBlank()) parts.add(item.subtitle)
            if (item.trackCount > 0) parts.add("${item.trackCount}首")
        }
        SearchType.PLAYLIST -> {
            if (item.trackCount > 0) parts.add("${item.trackCount}首歌曲")
            parts.add(item.platform.displayName)
        }
        else -> {
            if (item.subtitle.isNotBlank()) parts.add(item.subtitle)
        }
    }
    return parts.joinToString(" · ")
}
