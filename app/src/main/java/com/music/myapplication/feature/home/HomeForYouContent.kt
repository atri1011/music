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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.music.myapplication.domain.repository.RecentPlay
import com.music.myapplication.domain.repository.ToplistInfo
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.PlaylistCard
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.glassSurface
import com.music.myapplication.ui.theme.verticalGradientScrim
import kotlinx.coroutines.launch

@Composable
fun ForYouContent(
    state: HomeUiState,
    onNavigateToPlaylist: (id: String, platform: String, name: String, source: String) -> Unit,
    onNavigateToArtist: (artistId: String, platform: String, artistName: String) -> Unit,
    onRefreshGuessYouLike: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        if (state.dailyTracks.isNotEmpty()) {
            DailyRecommendCard(
                tracks = state.dailyTracks,
                onPlay = {
                    val track = state.dailyTracks.firstOrNull() ?: return@DailyRecommendCard
                    playerViewModel.playTrack(track, state.dailyTracks, 0)
                },
                modifier = Modifier.padding(horizontal = AppSpacing.Large)
            )
            Spacer(modifier = Modifier.height(AppSpacing.Medium))
        }

        state.fmTrack?.let { fmTrack ->
            PersonalFmCard(
                track = fmTrack,
                onPlay = { playerViewModel.playTrack(fmTrack, listOf(fmTrack), 0) },
                modifier = Modifier.padding(horizontal = AppSpacing.Large)
            )
            Spacer(modifier = Modifier.height(AppSpacing.Large))
        }

        if (state.continueListeningEntries.isNotEmpty()) {
            ContinueListeningSection(
                entries = state.continueListeningEntries,
                onPlayEntry = { index ->
                    val queue = state.continueListeningEntries.map { it.track }
                    if (index in queue.indices) {
                        playerViewModel.playTrack(queue[index], queue, index)
                    }
                }
            )
            Spacer(modifier = Modifier.height(AppSpacing.Large))
        }

        if (state.recentArtists.isNotEmpty()) {
            RecentArtistsSection(
                artists = state.recentArtists,
                onArtistClick = { artist ->
                    onNavigateToArtist(artist.seedTrackId, artist.platform.id, artist.artistName)
                }
            )
            Spacer(modifier = Modifier.height(AppSpacing.Large))
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
private fun ContinueListeningSection(
    entries: List<RecentPlay>,
    onPlayEntry: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = AppSpacing.Large)) {
        Text(
            text = "继续听",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = AppSpacing.Small)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
                .padding(vertical = 6.dp)
        ) {
            entries.take(3).forEachIndexed { index, entry ->
                ContinueListeningItem(
                    entry = entry,
                    onClick = { onPlayEntry(index) }
                )
            }
        }
    }
}

@Composable
private fun ContinueListeningItem(
    entry: RecentPlay,
    onClick: () -> Unit
) {
    val track = entry.track

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.Small))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(52.dp)
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
            Text(
                text = entry.continueListeningLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = QQMusicGreen,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun RecentArtistsSection(
    artists: List<HomeRecentArtist>,
    onArtistClick: (HomeRecentArtist) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = AppSpacing.Large)) {
        Text(
            text = "最近常听歌手",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = AppSpacing.Small)
        )
        artists.take(4).chunked(2).forEachIndexed { rowIndex, rowArtists ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)
            ) {
                rowArtists.forEach { artist ->
                    RecentArtistEntry(
                        artist = artist,
                        onClick = { onArtistClick(artist) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowArtists.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rowIndex < artists.take(4).chunked(2).lastIndex) {
                Spacer(modifier = Modifier.height(AppSpacing.Small))
            }
        }
    }
}

@Composable
private fun RecentArtistEntry(
    artist: HomeRecentArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium), pressScale = true)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = artist.coverUrl,
            contentDescription = artist.artistName,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.artistName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.recentArtistLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DailyRecommendCard(
    tracks: List<Track>,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = tracks.firstOrNull()?.coverUrl
    val artistNames = tracks.take(5).joinToString(" · ") { it.artist }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        if (coverUrl != null) {
            CoverImage(
                url = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalGradientScrim(
                    color = Color.Black.copy(alpha = 0.60f),
                    startY = 0.35f,
                    endY = 1f
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "每日推荐",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (artistNames.isNotBlank()) {
                Text(
                    text = artistNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.80f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onPlay,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
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
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
            contentPadding = PaddingValues(horizontal = AppSpacing.Large)
        ) {
            items(items = playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    name = playlist.name,
                    coverUrl = playlist.coverUrl,
                    onClick = {
                        onNavigateToPlaylist(playlist.id, Platform.NETEASE.id, playlist.name, "playlist")
                    },
                    shape = RoundedCornerShape(24.dp)
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
            Row(
                modifier = Modifier.weight(1f),
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
                    modifier = Modifier.size(28.dp)
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "播放",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (isLoading) {
                repeat(3) { GuessYouLikeItemPlaceholder() }
            } else {
                tracks.forEachIndexed { index, track ->
                    GuessYouLikeItem(track = track, onClick = { onPlayTrack(index) })
                }
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
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
            modifier = Modifier.size(20.dp)
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

private fun RecentPlay.continueListeningLabel(): String = when {
    playCount > 1 -> "最近听过 · $playCount 次"
    else -> "最近听过"
}

private fun HomeRecentArtist.recentArtistLabel(): String = when {
    listenCount > 1 -> "${platform.displayName} · $listenCount 次"
    else -> platform.displayName
}
