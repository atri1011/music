package com.music.myapplication.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
        if (state.dailyTracks.isNotEmpty() || state.fmTrack != null) {
            val daily30Track = state.dailyTracks.getOrNull(1) ?: state.fmTrack
            RecommendationFeatureRow(
                dailyTracks = state.dailyTracks,
                daily30Track = daily30Track,
                onPlayDaily = daily@{
                    val track = state.dailyTracks.firstOrNull() ?: return@daily
                    playerViewModel.playTrack(track, state.dailyTracks, 0)
                },
                onPlayDaily30 = daily30@{
                    val queue = state.dailyTracks.ifEmpty {
                        state.fmTrack?.let(::listOf).orEmpty()
                    }
                    val track = queue.firstOrNull() ?: return@daily30
                    playerViewModel.playTrack(track, queue, 0)
                }
            )
            Spacer(modifier = Modifier.height(AppSpacing.Large))
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
            Spacer(modifier = Modifier.height(AppSpacing.Large))
        }

        if (state.recommendedPlaylists.isNotEmpty()) {
            RecommendedPlaylistRow(
                playlists = state.recommendedPlaylists,
                onNavigateToPlaylist = onNavigateToPlaylist
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

        Spacer(modifier = Modifier.height(AppSpacing.XLarge))
    }
}

@Composable
private fun RecommendationFeatureRow(
    dailyTracks: List<Track>,
    daily30Track: Track?,
    onPlayDaily: () -> Unit,
    onPlayDaily30: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = ((maxWidth - 56.dp) / 2).coerceIn(150.dp, 220.dp)

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AppSpacing.Large),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium)
        ) {
            if (dailyTracks.isNotEmpty()) {
                item(key = "daily") {
                    DailyRecommendCard(
                        tracks = dailyTracks,
                        onPlay = onPlayDaily,
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
            item(key = "daily30") {
                DailyThirtyCard(
                    track = daily30Track,
                    onPlay = onPlayDaily30,
                    modifier = Modifier.width(cardWidth)
                )
            }
        }
    }
}

@Composable
private fun ContinueListeningSection(
    entries: List<RecentPlay>,
    onPlayEntry: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = AppSpacing.Large)) {
        HomeSectionTitle(
            title = "继续听, 接着刚才的节奏",
            modifier = Modifier.padding(bottom = AppSpacing.Small)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(AppShapes.Small)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(AppSpacing.XSmall))
                HomeTinyBadge(text = entry.continueListeningLabel())
            }
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Icon(
            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (track.isFavorite) "已收藏" else "收藏状态",
            tint = if (track.isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    showAccentIcon: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (showAccentIcon) {
            Spacer(modifier = Modifier.width(AppSpacing.XSmall))
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = QQMusicGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HomeTinyBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppShapes.Tiny)
    Box(
        modifier = modifier
            .widthIn(max = 96.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = shape
            )
            .padding(horizontal = AppSpacing.XXSmall, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    val featuredTrack = tracks.firstOrNull()
    val artistNames = tracks.take(5).joinToString(" · ") { it.artist }

    RecommendationFeatureCard(
        title = "For\nYou",
        subtitle = "猜你喜欢",
        trackTitle = featuredTrack?.title ?: "每日推荐",
        artist = featuredTrack?.artist?.takeIf { it.isNotBlank() } ?: artistNames,
        coverUrl = featuredTrack?.coverUrl.orEmpty(),
        lightGradient = listOf(Color(0xFFFFE4E6), Color(0xFFFFCDD5)),
        darkGradient = listOf(Color(0xFF4A1424), Color(0xFF32101A)),
        accentColor = Color(0xFFE11D48),
        onPlay = onPlay,
        modifier = modifier
    )
}

@Composable
private fun DailyThirtyCard(
    track: Track?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songLine = track?.let { "${it.title}-${it.artist}" } ?: "推送 API 待接入"

    RecommendationFeatureCard(
        title = "Daily\n30",
        subtitle = "每日30首",
        trackTitle = "每日30首",
        artist = songLine,
        coverUrl = track?.coverUrl.orEmpty(),
        lightGradient = listOf(Color(0xFFFF8EA1), Color(0xFFFF7F93)),
        darkGradient = listOf(Color(0xFF6A1F32), Color(0xFF4A1828)),
        accentColor = Color.White,
        onPlay = onPlay,
        modifier = modifier
    )
}

@Composable
private fun RecommendationFeatureCard(
    title: String,
    subtitle: String,
    trackTitle: String,
    artist: String,
    coverUrl: String,
    lightGradient: List<Color>,
    darkGradient: List<Color>,
    accentColor: Color,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val subtleTextColor = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF64748B)
    val cardShape = RoundedCornerShape(AppShapes.Large)

    Box(
        modifier = modifier
            .height(180.dp)
            .clip(cardShape)
            .background(Brush.linearGradient(if (isDark) darkGradient else lightGradient))
            .border(0.5.dp, Color.White.copy(alpha = if (isDark) 0.10f else 0.55f), cardShape)
            .clickable(onClick = onPlay)
            .padding(AppSpacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    ),
                    color = accentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = if (isDark) 0.78f else 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FeatureCover(
                coverUrl = coverUrl,
                contentDescription = trackTitle,
                accentColor = accentColor
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(end = 56.dp)
        ) {
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.labelSmall,
                color = subtleTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onPlay,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isDark) 0.20f else 0.90f))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = QQMusicGreen,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun FeatureCover(
    coverUrl: String,
    contentDescription: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(AppShapes.Small))
            .background(accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNotBlank()) {
            CoverImage(
                url = coverUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(34.dp)
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
        HomeSectionTitle(
            title = "热门歌单, 听点不一样的",
            showAccentIcon = true,
            modifier = Modifier.padding(
                start = AppSpacing.Large,
                end = AppSpacing.Large,
                bottom = AppSpacing.Small
            )
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Medium),
            contentPadding = PaddingValues(horizontal = AppSpacing.Large)
        ) {
            items(items = playlists, key = { it.id }) { playlist ->
                HomeProgramPlaylistCard(
                    playlist = playlist,
                    onClick = {
                        onNavigateToPlaylist(playlist.id, Platform.NETEASE.id, playlist.name, "playlist")
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeProgramPlaylistCard(
    playlist: ToplistInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(AppShapes.Medium))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(AppShapes.Medium)
                )
        ) {
            CoverImage(
                url = playlist.coverUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalGradientScrim(
                        color = Color.Black.copy(alpha = 0.28f),
                        startY = 0.10f,
                        endY = 1f
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppSpacing.XSmall)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.XSmall))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
                        tint = QQMusicGreen,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = refreshRotation.value }
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacing.XXSmall))
                HomeSectionTitle(
                    title = if (label.isNotBlank()) "周三的声音, $label" else "周三的声音, 不远不近",
                    showAccentIcon = true
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QQMusicGreen.copy(alpha = 0.10f))
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(14.dp),
                        tint = QQMusicGreen
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "播放",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = QQMusicGreen
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.XSmall)) {
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
            .clip(RoundedCornerShape(AppShapes.Small))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(AppShapes.Small)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.album.isNotBlank()) {
                    Spacer(modifier = Modifier.width(AppSpacing.XSmall))
                    HomeTinyBadge(text = track.album)
                }
            }
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Icon(
            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (track.isFavorite) "已收藏" else "收藏状态",
            tint = if (track.isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(21.dp)
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
