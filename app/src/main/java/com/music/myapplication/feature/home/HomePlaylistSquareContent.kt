package com.music.myapplication.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.feature.components.ChoicePill
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.PlatformFilterChips
import com.music.myapplication.feature.components.PlaylistCard
import com.music.myapplication.feature.components.ShimmerGridCard
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing

@Composable
fun PlaylistSquareContent(
    state: HomeUiState,
    onPlatformChange: (Platform) -> Unit,
    onCategoryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.playlistSquarePlatform, state.selectedPlaylistCategory) {
        gridState.scrollToItem(0)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.playlistItems.size - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.playlistItems.isNotEmpty()) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PlatformFilterChips(
            selectedPlatform = state.playlistSquarePlatform,
            onPlatformSelected = onPlatformChange,
            platforms = listOf(Platform.NETEASE, Platform.QQ),
            modifier = Modifier.padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.XXSmall)
        )

        if (state.playlistCategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AppSpacing.Medium, vertical = AppSpacing.XXSmall),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)
            ) {
                items(items = state.playlistCategories, key = { it.name }) { category ->
                    val selected = category.name == state.selectedPlaylistCategory
                    ChoicePill(
                        selected = selected,
                        onClick = { onCategoryChange(category.name) },
                        minHeight = 40.dp,
                        contentPadding = PaddingValues(
                            horizontal = AppSpacing.Small,
                            vertical = AppSpacing.XSmall
                        )
                    ) {
                        Text(
                            text = category.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        when {
            state.playlistSquareError != null && state.playlistItems.isEmpty() -> {
                ErrorView(message = state.playlistSquareError!!, onRetry = onRetry)
            }
            state.isPlaylistSquareLoading && state.playlistItems.isEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)
                ) {
                    items(9) {
                        ShimmerGridCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(AppShapes.Small))
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        state.playlistItems,
                        key = { "${it.platform.id}:${it.id}" }
                    ) { playlist ->
                        PlaylistCard(
                            name = playlist.name,
                            coverUrl = playlist.coverUrl,
                            onClick = {
                                onNavigateToPlaylist(
                                    playlist.id,
                                    playlist.platform.id,
                                    playlist.name,
                                    "playlist"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
