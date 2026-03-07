package com.music.myapplication.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.formatDuration
import com.music.myapplication.ui.theme.PlayerBgBottom
import com.music.myapplication.ui.theme.PlayerBgMiddle
import com.music.myapplication.ui.theme.PlayerBgTop
import com.music.myapplication.ui.theme.rememberDominantColorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayerLyricsScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
    val progress by playerViewModel.progressState.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyricsUiState.collectAsStateWithLifecycle()
    val trackInfoState by playerViewModel.trackInfoState.collectAsStateWithLifecycle()
    val currentTrack = staticState.currentTrack
    var hasLoadedTrack by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playerViewModel.showLyricsPanel()
    }

    LaunchedEffect(currentTrack?.id, currentTrack?.platform?.id) {
        if (currentTrack != null) {
            hasLoadedTrack = true
        } else if (hasLoadedTrack) {
            onBack()
        }
    }

    if (currentTrack == null) return

    val currentLyricIndex = remember(lyricsState.lyrics, progress.positionMs) {
        LyricsParser.findCurrentIndex(lyricsState.lyrics, progress.positionMs)
    }

    val dominantColorState = rememberDominantColorState(coverUrl = currentTrack.coverUrl)

    val animatedDominant by animateColorAsState(
        targetValue = dominantColorState.dominantColor,
        animationSpec = tween(800),
        label = "dominantColor"
    )
    val animatedMuted by animateColorAsState(
        targetValue = dominantColorState.mutedColor,
        animationSpec = tween(800),
        label = "mutedColor"
    )

    val titleColor = Color(0xFF31373D)
    val subtitleColor = Color(0xFF6A727A)
    val activeLyricColor = Color(0xFF161A1F)
    val inactiveLyricColor = Color(0xFF7A828B)
    val bottomTint by animateColorAsState(
        targetValue = lerp(animatedMuted, Color(0xFFEFB1BE), 0.65f),
        animationSpec = tween(800),
        label = "bottomTint"
    )

    // Pager state for 3 pages
    val pagerState = rememberPagerState(initialPage = 0) { 3 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PlayerBgTop, PlayerBgMiddle, PlayerBgBottom)
                )
            )
    ) {
        // Bottom warm tint overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to bottomTint.copy(alpha = 0.28f)
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(subtitleColor.copy(alpha = 0.3f))
                )
            }

            // Top bar: small cover + title/artist + favorite + more
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    url = currentTrack.coverUrl,
                    contentDescription = currentTrack.title,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = playerViewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (currentTrack.isFavorite) Color(0xFFE53935) else subtitleColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { /* more actions */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = subtitleColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // HorizontalPager (3 pages)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> CoverPage(
                        track = currentTrack,
                        isPlaying = staticState.isPlaying,
                        glowColor = animatedDominant,
                        lyrics = lyricsState.lyrics,
                        currentIndex = currentLyricIndex,
                        primaryLyricColor = activeLyricColor,
                        secondaryLyricColor = inactiveLyricColor,
                        onToggleFavorite = playerViewModel::toggleFavorite
                    )
                    1 -> LyricsPanelContent(
                        lyricsState = lyricsState,
                        currentIndex = currentLyricIndex,
                        activeLyricColor = activeLyricColor,
                        inactiveLyricColor = inactiveLyricColor,
                        scrimColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    )
                    2 -> SongInfoPage(
                        track = currentTrack,
                        trackInfoState = trackInfoState,
                        playerViewModel = playerViewModel
                    )
                }
            }

            // Page indicator (3 dots)
            PagerIndicator(
                pageCount = 3,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Progress bar + time
            PlayerProgressSection(
                progress = progress,
                onSeek = playerViewModel::seekTo,
                accentColor = animatedDominant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Play controls: prev / play-pause / next
            PlayControlRow(
                isPlaying = staticState.isPlaying,
                onPlayPause = playerViewModel::togglePlayPause,
                onPrevious = playerViewModel::skipPrevious,
                onNext = playerViewModel::skipNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
            )
        }
    }
}

@Composable
private fun CoverPage(
    track: Track,
    isPlaying: Boolean,
    glowColor: Color,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    primaryLyricColor: Color,
    secondaryLyricColor: Color,
    onToggleFavorite: () -> Unit
) {
    val previewLines = remember(lyrics, currentIndex, track.artist) {
        resolveCoverLyricsPreview(lyrics = lyrics, currentIndex = currentIndex, fallback = track.artist)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RotatingCover(
            coverUrl = track.coverUrl,
            isPlaying = isPlaying,
            glowColor = glowColor,
            modifier = Modifier.size(280.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = primaryLyricColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryLyricColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (track.isFavorite) Color(0xFFE53935) else secondaryLyricColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SongInfoPage(
    track: Track,
    trackInfoState: TrackInfoUiState,
    playerViewModel: PlayerViewModel
) {
    val scrollState = rememberScrollState()
    val subtitleColor = Color(0xFF6A727A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Song title + artist
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF31373D)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = subtitleColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Music Encyclopedia
        SectionHeader("音乐百科")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("曲风: 流行")
            InfoChip("语种: 国语")
            if (track.durationMs > 0) {
                InfoChip("时长: ${formatDuration(track.durationMs)}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Memory Coordinates
        SectionHeader("回忆坐标")
        Spacer(modifier = Modifier.height(8.dp))
        if (trackInfoState.firstPlayDate != null) {
            val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
            val season = remember(trackInfoState.firstPlayDate) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = trackInfoState.firstPlayDate }
                when (cal.get(java.util.Calendar.MONTH)) {
                    in 2..4 -> "春天"
                    in 5..7 -> "夏天"
                    in 8..10 -> "秋天"
                    else -> "冬天"
                }
            }
            val timeOfDay = remember(trackInfoState.firstPlayDate) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = trackInfoState.firstPlayDate }
                when (cal.get(java.util.Calendar.HOUR_OF_DAY)) {
                    in 0..5 -> "凌晨"
                    in 6..11 -> "上午"
                    in 12..13 -> "中午"
                    in 14..17 -> "下午"
                    in 18..20 -> "傍晚"
                    else -> "深夜"
                }
            }
            Text(
                text = "初次播放 ${dateFormat.format(Date(trackInfoState.firstPlayDate))} · $season · $timeOfDay",
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor
            )
        } else {
            Text(
                text = "尚未记录播放历史",
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor
            )
        }
        Text(
            text = "累计播放 ${trackInfoState.totalPlayCount} 次",
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Similar songs
        if (trackInfoState.similarTracks.isNotEmpty()) {
            SectionHeader("相似歌曲")
            Spacer(modifier = Modifier.height(8.dp))
            trackInfoState.similarTracks.forEach { similarTrack ->
                SimilarTrackItem(
                    track = similarTrack,
                    onClick = {
                        playerViewModel.playTrack(similarTrack, trackInfoState.similarTracks, trackInfoState.similarTracks.indexOf(similarTrack))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFF31373D)
    )
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF5A6370)
        )
    }
}

@Composable
private fun SimilarTrackItem(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.3f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF31373D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6A727A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color(0xFF5A6370),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) Color(0xFF31373D)
                        else Color(0xFF31373D).copy(alpha = 0.25f)
                    )
            )
        }
    }
}

@Composable
private fun PlayerProgressSection(
    progress: PlaybackProgressUiState,
    onSeek: (Long) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val progressFraction = if (progress.durationMs > 0) {
        if (sliderDragging) sliderPosition else progress.positionMs.toFloat() / progress.durationMs
    } else {
        0f
    }
    val subtleColor = Color(0xFF6A727A)

    Column(modifier = modifier) {
        Slider(
            value = progressFraction.coerceIn(0f, 1f),
            onValueChange = {
                sliderDragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                sliderDragging = false
                onSeek((sliderPosition * progress.durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF31373D).copy(alpha = 0.15f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(
                    if (sliderDragging) (sliderPosition * progress.durationMs).toLong() else progress.positionMs
                ),
                style = MaterialTheme.typography.labelSmall,
                color = subtleColor
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = subtleColor
            )
        }
    }
}

@Composable
private fun PlayControlRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = Color(0xFF31373D)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(36.dp),
                tint = contentColor
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(36.dp),
                tint = contentColor
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(36.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
private fun LyricsPanelContent(
    lyricsState: LyricsUiState,
    currentIndex: Int,
    activeLyricColor: Color,
    inactiveLyricColor: Color,
    scrimColor: Color,
    modifier: Modifier = Modifier
) {
    when {
        lyricsState.isLoading && lyricsState.lyrics.isEmpty() -> StatusHint(
            text = "歌词加载中...",
            modifier = modifier
        )
        !lyricsState.errorMessage.isNullOrBlank() && lyricsState.lyrics.isEmpty() -> StatusHint(
            text = lyricsState.errorMessage,
            modifier = modifier
        )
        else -> LyricsView(
            lyrics = lyricsState.lyrics,
            currentIndex = currentIndex,
            activeLineColor = activeLyricColor,
            inactiveLineColor = inactiveLyricColor,
            scrimColor = scrimColor,
            modifier = modifier
        )
    }
}

private data class CoverLyricsPreview(
    val currentLine: String,
    val nextLine: String
)

private fun resolveCoverLyricsPreview(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    fallback: String
): CoverLyricsPreview {
    if (lyrics.isEmpty()) {
        val line = fallback.ifBlank { "暂无歌词" }
        return CoverLyricsPreview(
            currentLine = line,
            nextLine = "轻触上方可切换歌词页"
        )
    }

    val safeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val currentLine = lyrics[safeIndex].text.ifBlank { fallback.ifBlank { "暂无歌词" } }
    val nextLine = lyrics.getOrNull(safeIndex + 1)?.text?.ifBlank { "" }.orEmpty()

    return CoverLyricsPreview(
        currentLine = currentLine,
        nextLine = if (nextLine.isBlank()) " " else nextLine
    )
}

@Composable
private fun StatusHint(text: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.orEmpty().ifBlank { "暂无歌词" },
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF6A727A),
            textAlign = TextAlign.Center
        )
    }
}
