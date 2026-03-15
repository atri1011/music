package com.music.myapplication.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.music.myapplication.ui.theme.AppShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaylistPreview
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ChoicePill
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.PlatformFilterChips
import com.music.myapplication.feature.components.PlaylistCard
import com.music.myapplication.feature.components.SegmentedChoiceRow
import com.music.myapplication.feature.components.ShimmerGridCard
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.glassSurface
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit,
    onNavigateToSearch: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gradientColor = MaterialTheme.colorScheme.primary.copy(
        alpha = if (isDark) 0.15f else 0.08f
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(gradientColor, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar: title + icon group
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "首页",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { /* scan */ }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onNavigateToSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.loadToplists(); viewModel.loadRecommendations() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Top section switcher
            val tabs = listOf("为你推荐", "榜单", "歌单广场")
            SegmentedChoiceRow(
                items = tabs.indices.toList(),
                selectedItem = state.selectedTab,
                onItemSelected = viewModel::onTabChange,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            ) { index, selected ->
                Text(
                    text = tabs[index],
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }

            when (state.selectedTab) {
                0 -> ForYouContent(
                    state = state,
                    onNavigateToPlaylist = onNavigateToPlaylist,
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
                    onLoadMore = viewModel::loadMorePlaylistSquare,
                    onRetry = viewModel::retryPlaylistSquare,
                    onNavigateToPlaylist = onNavigateToPlaylist
                )
            }
        }
    }
}

@Composable
private fun ForYouContent(
    state: HomeUiState,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit,
    onRefreshGuessYouLike: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val scrollState = rememberScrollState()
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 6 -> "夜深了"
            hour < 12 -> "早上好"
            hour < 14 -> "中午好"
            hour < 18 -> "下午好"
            else -> "晚上好"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Greeting
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            text = "今天想听点什么?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Daily Recommendation Card
        if (state.dailyTracks.isNotEmpty()) {
            DailyRecommendCard(
                tracks = state.dailyTracks,
                onPlay = {
                    val track = state.dailyTracks.firstOrNull() ?: return@DailyRecommendCard
                    playerViewModel.playTrack(track, state.dailyTracks, 0)
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Personal FM
        state.fmTrack?.let { fmTrack ->
            PersonalFmCard(
                track = fmTrack,
                onPlay = {
                    playerViewModel.playTrack(fmTrack, listOf(fmTrack), 0)
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Recommended Playlists - horizontal scroll
        if (state.recommendedPlaylists.isNotEmpty()) {
            RecommendedPlaylistRow(
                playlists = state.recommendedPlaylists,
                onNavigateToPlaylist = onNavigateToPlaylist
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Guess You Like
        if (state.guessYouLikeTracks.isNotEmpty() || state.isGuessYouLikeLoading) {
            GuessYouLikeSection(
                label = state.guessYouLikeLabel,
                tracks = state.guessYouLikeTracks,
                isLoading = state.isGuessYouLikeLoading,
                onRefresh = onRefreshGuessYouLike,
                onPlayAll = {
                    if (state.guessYouLikeTracks.isNotEmpty()) {
                        playerViewModel.playTrack(
                            state.guessYouLikeTracks.first(),
                            state.guessYouLikeTracks,
                            0
                        )
                    }
                },
                onPlayTrack = { index ->
                    playerViewModel.playTrack(
                        state.guessYouLikeTracks[index],
                        state.guessYouLikeTracks,
                        index
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DailyRecommendCard(
    tracks: List<Track>,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = remember { Calendar.getInstance() }
    val dayOfMonth = remember { calendar.get(Calendar.DAY_OF_MONTH) }
    val dayNames = remember { arrayOf("日", "一", "二", "三", "四", "五", "六") }
    val dayOfWeek = remember { dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1] }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.Medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: date badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$dayOfMonth",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "周$dayOfWeek",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Middle: description + thumbnails
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "每日推荐",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "优先根据最近播放和收藏生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                // 3 small cover thumbnails
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tracks.take(3).forEach { track ->
                        CoverImage(
                            url = track.coverUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(AppShapes.ExtraSmall)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: green play button
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(QQMusicGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonalFmCard(
    track: Track,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.Medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(AppShapes.Small)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "私人FM",
                style = MaterialTheme.typography.labelMedium,
                color = QQMusicGreen,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onPlay,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(QQMusicGreen.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = QQMusicGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RecommendedPlaylistRow(
    playlists: List<ToplistInfo>,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit
) {
    Column {
        Text(
            text = "推荐歌单",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable {
                            onNavigateToPlaylist(playlist.id, Platform.NETEASE.id, playlist.name, "playlist")
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(AppShapes.Small))
                    ) {
                        CoverImage(
                            url = playlist.coverUrl,
                            contentDescription = playlist.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GuessYouLikeSection(
    label: String,
    tracks: List<Track>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit
) {
    val refreshRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (!isLoading) {
                        scope.launch {
                            refreshRotation.animateTo(
                                targetValue = refreshRotation.value + 360f,
                                animationSpec = tween(500)
                            )
                        }
                        onRefresh()
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = refreshRotation.value }
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (label.isNotBlank()) "猜你喜欢 · $label" else "猜你喜欢",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppShapes.Medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "播放",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Song list
        if (isLoading) {
            repeat(3) {
                GuessYouLikeItemPlaceholder()
                if (it < 2) Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            tracks.forEachIndexed { index, track ->
                GuessYouLikeItem(
                    track = track,
                    onClick = { onPlayTrack(index) }
                )
                if (index < tracks.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GuessYouLikeItem(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.ExtraSmall))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(AppShapes.ExtraSmall)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun GuessYouLikeItemPlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(AppShapes.ExtraSmall))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(AppShapes.Tiny))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(AppShapes.Tiny))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
private fun ChartContent(
    state: HomeUiState,
    onPlatformChange: (com.music.myapplication.domain.model.Platform) -> Unit,
    onRetry: () -> Unit,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PlatformFilterChips(
            selectedPlatform = state.platform,
            onPlatformSelected = onPlatformChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when {
            state.error != null -> {
                ErrorView(message = state.error!!, onRetry = onRetry)
            }
            state.isLoading -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(4) {
                        ShimmerGridCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(AppShapes.Medium))
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        state.toplists,
                        key = { it.id },
                        contentType = { "toplist_card" }
                    ) { toplist ->
                        ToplistPreviewCard(
                            toplist = toplist,
                            previewTracks = state.toplistPreviews[toplist.id],
                            onClick = {
                                onNavigateToPlaylist(toplist.id, state.platform.id, toplist.name, "toplist")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToplistPreviewCard(
    toplist: ToplistInfo,
    previewTracks: List<Track>?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // Header: title + update frequency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = toplist.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "每天更新",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Body: cover image + song list
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Cover image with play button overlay
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(AppShapes.Small)),
                contentAlignment = Alignment.Center
            ) {
                CoverImage(
                    url = toplist.coverUrl,
                    contentDescription = toplist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Play button overlay
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song preview list
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (previewTracks != null) {
                    previewTracks.forEachIndexed { index, track ->
                        ChartSongRow(rank = index + 1, track = track)
                    }
                } else {
                    // Loading placeholder
                    repeat(3) { index ->
                        ChartSongRowPlaceholder(rank = index + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSongRow(rank: Int, track: Track) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (rank <= 3) QQMusicGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = " - ${track.artist}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChartSongRowPlaceholder(rank: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(AppShapes.Tiny))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun PlaylistSquareContent(
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Category pills
        if (state.playlistCategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.playlistCategories,
                    key = { it.name }
                ) { category ->
                    val selected = category.name == state.selectedPlaylistCategory
                    ChoicePill(
                        selected = selected,
                        onClick = { onCategoryChange(category.name) }
                    ) {
                        Text(
                            text = category.name,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        when {
            state.playlistSquareError != null && state.playlistItems.isEmpty() -> {
                ErrorView(
                    message = state.playlistSquareError!!,
                    onRetry = onRetry
                )
            }
            state.isPlaylistSquareLoading && state.playlistItems.isEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
