package com.music.myapplication.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.appPremiumBackground

@Composable
fun HomeScreen(
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit,
    onNavigateToArtist: (artistId: String, platform: String, artistName: String) -> Unit,
    onNavigateToSearch: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf("推荐", "榜单", "歌单")
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .appPremiumBackground(
                primary = backgroundColor,
                secondary = backgroundColor
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            HomeScreenTopBar(
                onNavigateToSearch = onNavigateToSearch,
                onRefresh = {
                    viewModel.loadToplists()
                    viewModel.loadRecommendations()
                },
                tabs = tabs,
                selectedTab = state.selectedTab,
                onTabSelected = viewModel::onTabChange
            )

            when (state.selectedTab) {
                0 -> ForYouContent(
                    state = state,
                    onNavigateToPlaylist = onNavigateToPlaylist,
                    onNavigateToArtist = onNavigateToArtist,
                    onRefreshGuessYouLike = viewModel::refreshGuessYouLike,
                    playerViewModel = playerViewModel
                )
                1 -> ChartContent(
                    state = state,
                    onPlatformChange = viewModel::onPlatformChange,
                    onRetry = { viewModel.loadToplists() },
                    onNavigateToPlaylist = onNavigateToPlaylist
                )
                2 -> PlaylistSquareContent(
                    state = state,
                    onPlatformChange = viewModel::onPlaylistSquarePlatformChange,
                    onCategoryChange = viewModel::onPlaylistCategoryChange,
                    onRefresh = viewModel::refreshPlaylistSquare,
                    onLoadMore = viewModel::loadMorePlaylistSquare,
                    onRetry = viewModel::retryPlaylistSquare,
                    onNavigateToPlaylist = onNavigateToPlaylist
                )
            }
        }
    }
}
