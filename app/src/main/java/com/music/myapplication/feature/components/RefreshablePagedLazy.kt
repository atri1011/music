package com.music.myapplication.feature.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

const val LoadMoreBufferSize = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshablePagedLazyColumn(
    itemCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    loadMoreBuffer: Int = LoadMoreBufferSize,
    content: LazyListScope.() -> Unit
) {
    ObserveListLoadMore(
        state = state,
        itemCount = itemCount,
        canLoadMore = canLoadMore,
        isRefreshing = isRefreshing,
        isLoadingMore = isLoadingMore,
        loadMoreBuffer = loadMoreBuffer,
        onLoadMore = onLoadMore
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment
        ) {
            content()
            if (isLoadingMore) {
                item(key = "load_more_footer", contentType = "load_more_footer") {
                    LoadMoreFooter()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshablePagedLazyVerticalGrid(
    columns: GridCells,
    itemCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    loadMoreBuffer: Int = LoadMoreBufferSize,
    content: LazyGridScope.() -> Unit
) {
    ObserveGridLoadMore(
        state = state,
        itemCount = itemCount,
        canLoadMore = canLoadMore,
        isRefreshing = isRefreshing,
        isLoadingMore = isLoadingMore,
        loadMoreBuffer = loadMoreBuffer,
        onLoadMore = onLoadMore
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyVerticalGrid(
            columns = columns,
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement
        ) {
            content()
            if (isLoadingMore) {
                item(
                    key = "load_more_footer",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "load_more_footer"
                ) {
                    LoadMoreFooter()
                }
            }
        }
    }
}

@Composable
fun LoadMoreFooter(
    modifier: Modifier = Modifier,
    text: String = "正在加载更多"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun ObserveListLoadMore(
    state: LazyListState,
    itemCount: Int,
    canLoadMore: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    loadMoreBuffer: Int,
    onLoadMore: () -> Unit
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, itemCount, canLoadMore, isRefreshing, isLoadingMore, loadMoreBuffer) {
        snapshotFlow { state.shouldLoadMore(itemCount, loadMoreBuffer) }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && canLoadMore && !isRefreshing && !isLoadingMore) {
                    latestOnLoadMore()
                }
            }
    }
}

@Composable
private fun ObserveGridLoadMore(
    state: LazyGridState,
    itemCount: Int,
    canLoadMore: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    loadMoreBuffer: Int,
    onLoadMore: () -> Unit
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, itemCount, canLoadMore, isRefreshing, isLoadingMore, loadMoreBuffer) {
        snapshotFlow { state.shouldLoadMore(itemCount, loadMoreBuffer) }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && canLoadMore && !isRefreshing && !isLoadingMore) {
                    latestOnLoadMore()
                }
            }
    }
}

private fun LazyListState.shouldLoadMore(itemCount: Int, loadMoreBuffer: Int): Boolean {
    if (itemCount <= 0) return false
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    val triggerIndex = (itemCount - loadMoreBuffer).coerceAtLeast(0)
    return lastVisibleIndex >= triggerIndex
}

private fun LazyGridState.shouldLoadMore(itemCount: Int, loadMoreBuffer: Int): Boolean {
    if (itemCount <= 0) return false
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    val triggerIndex = (itemCount - loadMoreBuffer).coerceAtLeast(0)
    return lastVisibleIndex >= triggerIndex
}
