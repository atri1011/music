package com.music.myapplication.feature.player

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.music.myapplication.core.common.normalizeCoverUrl
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.formatDuration
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

    val pagerState = rememberPagerState(initialPage = 0) { 3 }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Blurred album cover background
        BlurredCoverBackground(coverUrl = currentTrack.coverUrl)

        // Layer 2: Content
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
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            // Top bar area — crossfade between minimal (cover page) and full (lyrics/info)
            Crossfade(
                targetState = pagerState.currentPage == 0,
                animationSpec = tween(300),
                label = "topBarCrossfade"
            ) { isCoverPage ->
                if (isCoverPage) {
                    // Cover page: only more button on right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* more */ }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    // Lyrics/Info page: small cover + title/artist + heart + more
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverImage(
                            url = currentTrack.coverUrl,
                            contentDescription = currentTrack.title,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = playerViewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (currentTrack.isFavorite) Color(0xFFE53935) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { /* more */ }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
                        lyrics = lyricsState.lyrics,
                        currentIndex = currentLyricIndex,
                        onToggleFavorite = playerViewModel::toggleFavorite
                    )
                    1 -> LyricsPanelContent(
                        lyricsState = lyricsState,
                        currentIndex = currentLyricIndex,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
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
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Play controls
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

// ── Blurred Background ──────────────────────────────────────────────────────

@Composable
private fun BlurredCoverBackground(coverUrl: String) {
    val context = LocalContext.current
    val normalizedUrl = remember(coverUrl) { normalizeCoverUrl(coverUrl) }
    val useNativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier = Modifier.fillMaxSize()) {
        val imageRequest = remember(normalizedUrl, useNativeBlur) {
            ImageRequest.Builder(context)
                .data(normalizedUrl.ifEmpty { null })
                .size(if (useNativeBlur) 256 else 32)
                .scale(Scale.FILL)
                .crossfade(800)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }

        val painter = rememberAsyncImagePainter(model = imageRequest)

        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (useNativeBlur) Modifier.blur(60.dp) else Modifier
                ),
            contentScale = ContentScale.Crop
        )

        // Dark scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
    }
}

// ── Cover Page ──────────────────────────────────────────────────────────────

@Composable
private fun CoverPage(
    track: Track,
    isPlaying: Boolean,
    lyrics: List<LyricLine>,
    currentIndex: Int,
    onToggleFavorite: () -> Unit
) {
    val previewLines = remember(lyrics, currentIndex, track.artist) {
        resolveCoverLyricsPreview(lyrics = lyrics, currentIndex = currentIndex, fallback = track.artist)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RotatingCover(
            coverUrl = track.coverUrl,
            isPlaying = isPlaying,
            glowColor = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(280.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
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
                color = Color.White.copy(alpha = 0.65f),
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
                    tint = if (track.isFavorite) Color(0xFFE53935) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Lyrics Panel ────────────────────────────────────────────────────────────

@Composable
private fun LyricsPanelContent(
    lyricsState: LyricsUiState,
    currentIndex: Int,
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
            activeLineColor = Color.White,
            inactiveLineColor = Color.White.copy(alpha = 0.4f),
            translationColor = Color.White.copy(alpha = 0.6f),
            modifier = modifier
        )
    }
}

// ── Song Info Page ──────────────────────────────────────────────────────────

@Composable
private fun SongInfoPage(
    track: Track,
    trackInfoState: TrackInfoUiState,
    playerViewModel: PlayerViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                color = Color.White.copy(alpha = 0.65f)
            )
        } else {
            Text(
                text = "尚未记录播放历史",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
        Text(
            text = "累计播放 ${trackInfoState.totalPlayCount} 次",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (trackInfoState.similarTracks.isNotEmpty()) {
            SectionHeader("相似歌曲")
            Spacer(modifier = Modifier.height(8.dp))
            trackInfoState.similarTracks.forEach { similarTrack ->
                SimilarTrackItem(
                    track = similarTrack,
                    onClick = {
                        playerViewModel.playTrack(
                            similarTrack,
                            trackInfoState.similarTracks,
                            trackInfoState.similarTracks.indexOf(similarTrack)
                        )
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
        color = Color.White
    )
}

@Composable
private fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
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
            .background(Color.White.copy(alpha = 0.10f))
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
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}

// ── Page Indicator ──────────────────────────────────────────────────────────

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
                        if (index == currentPage) Color.White
                        else Color.White.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

// ── Progress Section ────────────────────────────────────────────────────────

@Composable
private fun PlayerProgressSection(
    progress: PlaybackProgressUiState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val progressFraction = if (progress.durationMs > 0) {
        if (sliderDragging) sliderPosition else progress.positionMs.toFloat() / progress.durationMs
    } else {
        0f
    }

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
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
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
                color = Color.White.copy(alpha = 0.55f)
            )
            Text(
                text = formatDuration(progress.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

// ── Play Controls ───────────────────────────────────────────────────────────

@Composable
private fun PlayControlRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                tint = Color.White
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

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
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
