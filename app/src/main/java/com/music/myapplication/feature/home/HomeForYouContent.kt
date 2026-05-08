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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.PlaylistCard
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.glassSurface
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun ForYouContent(
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
        Spacer(modifier = Modifier.height(AppSpacing.Medium))

        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = AppSpacing.Large)
        )
        Text(
            text = "今天想听点什么？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.XXSmall, start = AppSpacing.Large, end = AppSpacing.Large)
        )

        Spacer(modifier = Modifier.height(AppSpacing.Large))

        if (state.dailyTracks.isNotEmpty()) {
            DailyRecommendCard(
                tracks = state.dailyTracks,
                onPlay = {
                    val track = state.dailyTracks.firstOrNull() ?: return@DailyRecommendCard
                    playerViewModel.playTrack(track, state.dailyTracks, 0)
                },
                modifier = Modifier.padding(horizontal = AppSpacing.Large)
            )
            Spacer(modifier = Modifier.height(AppSpacing.Large))
        }

        state.fmTrack?.let { fmTrack ->
            PersonalFmCard(
                track = fmTrack,
                onPlay = { playerViewModel.playTrack(fmTrack, listOf(fmTrack), 0) },
                modifier = Modifier.padding(horizontal = AppSpacing.Large)
            )
            Spacer(modifier = Modifier.height(AppSpacing.XLarge))
        }

        if (state.recommendedPlaylists.isNotEmpty()) {
            RecommendedPlaylistRow(
                playlists = state.recommendedPlaylists,
                onNavigateToPlaylist = onNavigateToPlaylist
            )
            Spacer(modifier = Modifier.height(AppSpacing.XLarge))
        }

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

        Spacer(modifier = Modifier.height(AppSpacing.XLarge))
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
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
            .padding(horizontal = 18.dp, vertical = AppSpacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Spacer(modifier = Modifier.width(AppSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "每日推荐",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "优先根据最近播放和收藏生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = AppSpacing.XSmall)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.XXSmall)) {
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
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large), pressScale = true)
            .clickable(onClick = onPlay)
            .padding(14.dp),
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
                .background(QQMusicGreen.copy(alpha = 0.14f))
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
            modifier = Modifier.padding(start = AppSpacing.Large, bottom = AppSpacing.Small)
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small),
            contentPadding = PaddingValues(horizontal = AppSpacing.Large)
        ) {
            androidx.compose.foundation.lazy.items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    name = playlist.name,
                    coverUrl = playlist.coverUrl,
                    onClick = {
                        onNavigateToPlaylist(playlist.id, Platform.NETEASE.id, playlist.name, "playlist")
                    }
                )
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

    Column(modifier = Modifier.padding(horizontal = AppSpacing.Large)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppSpacing.Small),
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
            Spacer(modifier = Modifier.width(AppSpacing.XXSmall))
            Text(
                text = if (label.isNotBlank()) "猜你喜欢 · $label" else "猜你喜欢",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppShapes.Medium))
                    .glassSurface(shape = RoundedCornerShape(999.dp), pressScale = true)
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
                    Text(text = "播放", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (isLoading) {
            repeat(3) {
                GuessYouLikeItemPlaceholder()
                if (it < 2) Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            tracks.forEachIndexed { index, track ->
                GuessYouLikeItem(track = track, onClick = { onPlayTrack(index) })
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
        Spacer(modifier = Modifier.width(AppSpacing.Small))
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
            contentDescription = "播放",
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
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(AppShapes.Tiny))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(AppSpacing.XXSmall))
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
