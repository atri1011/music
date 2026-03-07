package com.music.myapplication.feature.discover

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.ErrorView
import com.music.myapplication.feature.components.PlatformFilterChips
import com.music.myapplication.feature.components.ShimmerGridCard
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.glassSurface
import com.music.myapplication.ui.theme.verticalGradientScrim

private val DiscoverScenes = listOf(
    DiscoverScene(
        id = "commute",
        title = "通勤",
        subtitle = "节奏别掉，路上就得带劲",
        keywords = listOf("通勤", "早高峰", "上班", "出发", "城市", "节奏"),
        fallbackShift = 0
    ),
    DiscoverScene(
        id = "focus",
        title = "专注",
        subtitle = "写代码、学习、发呆都能稳住",
        keywords = listOf("学习", "专注", "轻音乐", "纯音", "钢琴", "白噪音"),
        fallbackShift = 1
    ),
    DiscoverScene(
        id = "night",
        title = "夜晚",
        subtitle = "适合关灯以后慢慢听",
        keywords = listOf("夜", "深夜", "晚安", "治愈", "emo", "安静"),
        fallbackShift = 2
    ),
    DiscoverScene(
        id = "workout",
        title = "运动",
        subtitle = "跑起来，别磨叽",
        keywords = listOf("运动", "跑步", "燃", "健身", "电音", "热血"),
        fallbackShift = 3
    ),
    DiscoverScene(
        id = "weekend",
        title = "周末",
        subtitle = "松弛一点，听点不一样的",
        keywords = listOf("周末", "旅行", "公路", "派对", "放松", "chill"),
        fallbackShift = 4
    ),
    DiscoverScene(
        id = "healing",
        title = "治愈",
        subtitle = "心烦的时候来这儿缓一口",
        keywords = listOf("治愈", "温柔", "疗愈", "情绪", "民谣", "舒缓"),
        fallbackShift = 5
    )
)

private data class DiscoverScene(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val fallbackShift: Int
)

@Composable
fun DiscoverScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedScene = remember(state.selectedSceneId) {
        DiscoverScenes.firstOrNull { it.id == state.selectedSceneId } ?: DiscoverScenes.first()
    }
    val scenePlaylists = remember(state.themePlaylists, selectedScene) {
        state.themePlaylists.rankForScene(selectedScene)
    }
    val isRefreshing = state.isThemeLoading || state.isSimilarLoading || state.isHotLoading
    val showFatalError = !isRefreshing &&
        scenePlaylists.isEmpty() &&
        state.similarTracks.isEmpty() &&
        state.hotToplists.isEmpty() &&
        listOf(state.themeError, state.similarError, state.hotError).any { !it.isNullOrBlank() }

    if (showFatalError) {
        ErrorView(
            message = listOf(state.themeError, state.similarError, state.hotError)
                .filterNotNull()
                .joinToString(separator = "\n"),
            onRetry = viewModel::refresh,
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            DiscoverTopBar(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            )
        }

        item {
            SearchHeroCard(onClick = onNavigateToSearch)
        }

        item {
            SectionHeader(
                title = "场景速达",
                subtitle = "先定个氛围，再往下逛"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(DiscoverScenes, key = { it.id }) { scene ->
                    SceneShortcutCard(
                        scene = scene,
                        selected = scene.id == state.selectedSceneId,
                        onClick = { viewModel.onSceneChange(scene.id) }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "${selectedScene.title}歌单",
                subtitle = selectedScene.subtitle
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                state.isThemeLoading && scenePlaylists.isEmpty() -> {
                    LoadingRow()
                }

                scenePlaylists.isNotEmpty() -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(scenePlaylists, key = { it.id }) { playlist ->
                            ThemePlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    onNavigateToPlaylist(
                                        playlist.id,
                                        Platform.NETEASE.id,
                                        playlist.name,
                                        "playlist"
                                    )
                                }
                            )
                        }
                    }
                }

                else -> {
                    InlineMessageCard(
                        title = "主题歌单还没刷出来",
                        subtitle = state.themeError ?: "先点右上角刷新，或者直接去搜索碰运气。"
                    )
                }
            }
        }

        item {
            val seedTitle = state.similarSeedTrack?.title?.takeIf { it.isNotBlank() } ?: "你的最近口味"
            SectionHeader(
                title = "因为你刚听了「$seedTitle」",
                subtitle = "顺着这条线继续挖，说不定有惊喜"
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                state.isSimilarLoading && state.similarTracks.isEmpty() -> {
                    LoadingRow()
                }

                state.similarTracks.isNotEmpty() -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(state.similarTracks, key = { "${it.platform.id}:${it.id}" }) { track ->
                            SimilarTrackCard(
                                track = track,
                                onClick = {
                                    val index = state.similarTracks.indexOf(track)
                                    if (index >= 0) {
                                        playerViewModel.playTrack(track, state.similarTracks, index)
                                    }
                                }
                            )
                        }
                    }
                }

                else -> {
                    InlineMessageCard(
                        title = "这里还不够懂你",
                        subtitle = state.similarError ?: "先随便听几首、收藏几首，发现页就会聪明不少。"
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "多平台正在热",
                subtitle = "切个平台看看，聚合播放器的好处就在这"
            )
            Spacer(modifier = Modifier.height(12.dp))
            PlatformFilterChips(
                selectedPlatform = state.selectedPlatform,
                onPlatformSelected = viewModel::onPlatformChange
            )
            Spacer(modifier = Modifier.height(14.dp))
            when {
                state.isHotLoading && state.hotToplists.isEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .glassSurface()
                                    .padding(12.dp)
                            ) {
                                ShimmerGridCard(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                state.hotToplists.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.hotToplists.forEach { topList ->
                            HotToplistCard(
                                topList = topList,
                                previewTracks = state.hotToplistPreviews[topList.id].orEmpty(),
                                platform = state.selectedPlatform,
                                onClick = {
                                    onNavigateToPlaylist(
                                        topList.id,
                                        state.selectedPlatform.id,
                                        topList.name,
                                        "toplist"
                                    )
                                }
                            )
                        }
                    }
                }

                else -> {
                    InlineMessageCard(
                        title = "热榜暂时没拉回来",
                        subtitle = state.hotError ?: "网络缓一会儿，待会儿再戳一下刷新。"
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "发现",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "别光听熟的，往外再探一步",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新"
                )
            }
        }
    }
}

@Composable
private fun SearchHeroCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        QQMusicGreen.copy(alpha = 0.26f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "搜歌名、歌手、风格关键词",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "主流平台都能查，别闷着只听老三样",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SceneShortcutCard(
    scene: DiscoverScene,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                } else {
                    Modifier.glassSurface(RoundedCornerShape(20.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = scene.title,
                    fontWeight = FontWeight.SemiBold
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Text(
            text = scene.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun ThemePlaylistCard(
    playlist: ToplistInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            CoverImage(
                url = playlist.coverUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                showShimmer = true
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalGradientScrim(
                        color = Color.Black.copy(alpha = 0.5f),
                        startY = 0.2f,
                        endY = 1f
                    )
            )
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }
        Text(
            text = playlist.description.ifBlank { "点进去看看，说不定正对你胃口。" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun SimilarTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            CoverImage(
                url = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                showShimmer = true
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalGradientScrim(
                        color = Color.Black.copy(alpha = 0.56f),
                        startY = 0.3f,
                        endY = 1f
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(QQMusicGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White
                )
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun HotToplistCard(
    topList: ToplistInfo,
    previewTracks: List<Track>,
    platform: Platform,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .glassSurface(RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = topList.coverUrl,
            contentDescription = topList.name,
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            showShimmer = true
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topList.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )

            if (previewTracks.isEmpty()) {
                Text(
                    text = "点进去看看完整榜单。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                previewTracks.forEachIndexed { index, track ->
                    Text(
                        text = "${index + 1}. ${track.title} · ${track.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InlineMessageCard(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun LoadingRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(3) {
            ShimmerGridCard(
                modifier = Modifier.width(168.dp)
            )
        }
    }
}

private fun List<ToplistInfo>.rankForScene(scene: DiscoverScene): List<ToplistInfo> {
    if (isEmpty()) return emptyList()

    val scoredPlaylists: List<Pair<ToplistInfo, Int>> = map { playlist: ToplistInfo ->
        val haystack = buildString {
            append(playlist.name)
            append(' ')
            append(playlist.description)
        }.lowercase()

        val keywordScore = scene.keywords.fold(0) { total: Int, keyword: String ->
            total + if (haystack.contains(keyword.lowercase())) 2 else 0
        }
        val score = keywordScore + if (playlist.description.isNotBlank()) 1 else 0

        playlist to score
    }

    val matchedPlaylists: List<ToplistInfo> = scoredPlaylists
        .filter { pair: Pair<ToplistInfo, Int> -> pair.second > 0 }
        .sortedByDescending { pair: Pair<ToplistInfo, Int> -> pair.second }
        .map { pair: Pair<ToplistInfo, Int> -> pair.first }

    if (matchedPlaylists.isNotEmpty()) return matchedPlaylists.take(8)

    val safeShift = scene.fallbackShift % size
    return drop(safeShift).plus(take(safeShift)).take(8)
}
