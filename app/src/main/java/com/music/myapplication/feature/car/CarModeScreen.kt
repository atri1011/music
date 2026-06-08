package com.music.myapplication.feature.car

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.feature.components.formatDuration
import com.music.myapplication.feature.player.PlaybackProgressUiState
import com.music.myapplication.feature.player.PlayerStaticUiState
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.appPremiumBackground
import com.music.myapplication.ui.theme.glassSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarModeScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {}
) {
    val staticState by playerViewModel.staticUiState.collectAsStateWithLifecycle()
    val progress by playerViewModel.progressState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("车载模式") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appPremiumBackground(
                    primary = QQMusicGreen,
                    secondary = Color(0xFF3A7DFF),
                    base = Color(0xFF06110D),
                    grainAlpha = 0.018f
                )
                .padding(padding)
        ) {
            val track = staticState.currentTrack
            if (track == null) {
                CarModeEmptyState(
                    onNavigateToHome = onNavigateToHome,
                    modifier = Modifier.fillMaxSize()
                )
                return@Box
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                if (isLandscape) {
                    LandscapeCarModeContent(
                        track = track,
                        staticState = staticState,
                        progress = progress,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onPrevious = playerViewModel::skipPrevious,
                        onNext = playerViewModel::skipNext,
                        onFavorite = playerViewModel::toggleFavorite
                    )
                } else {
                    PortraitCarModeContent(
                        track = track,
                        staticState = staticState,
                        progress = progress,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onPrevious = playerViewModel::skipPrevious,
                        onNext = playerViewModel::skipNext,
                        onFavorite = playerViewModel::toggleFavorite
                    )
                }
            }
        }
    }
}

@Composable
private fun CarModeEmptyState(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(58.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "还没有正在播放的歌曲",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "先开始播放，再进入车载模式会显示大按钮控制。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        FilledTonalButton(onClick = onNavigateToHome) {
            Icon(Icons.Filled.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("去发现")
        }
    }
}

@Composable
private fun PortraitCarModeContent(
    track: Track,
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TrackHero(
            track = track,
            artworkSize = 220.dp,
            modifier = Modifier.fillMaxWidth()
        )
        CarProgress(progress = progress, modifier = Modifier.fillMaxWidth())
        CarControls(
            staticState = staticState,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onFavorite = onFavorite,
            contentPadding = PaddingValues(bottom = 18.dp)
        )
    }
}

@Composable
private fun LandscapeCarModeContent(
    track: Track,
    staticState: PlayerStaticUiState,
    progress: PlaybackProgressUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackHero(
            track = track,
            artworkSize = 176.dp,
            modifier = Modifier.weight(0.88f)
        )
        Column(
            modifier = Modifier.weight(1.12f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CarProgress(progress = progress, modifier = Modifier.fillMaxWidth())
            CarControls(
                staticState = staticState,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onFavorite = onFavorite,
                contentPadding = PaddingValues(0.dp)
            )
        }
    }
}

@Composable
private fun TrackHero(
    track: Track,
    artworkSize: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CoverImage(
            url = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(artworkSize)
                .clip(RoundedCornerShape(AppShapes.Large))
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = track.title.ifBlank { "未知歌曲" },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.artist.ifBlank { "未知歌手" },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CarProgress(
    progress: PlaybackProgressUiState,
    modifier: Modifier = Modifier
) {
    val duration = progress.durationMs.coerceAtLeast(0L)
    val position = progress.positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
    val fraction = if (duration > 0L) position.toFloat() / duration.toFloat() else 0f
    Column(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = QQMusicGreen,
            trackColor = Color.White.copy(alpha = 0.16f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(position),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.82f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun CarControls(
    staticState: PlayerStaticUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CarControlButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "上一首",
                size = 84.dp,
                onClick = onPrevious
            )
            CarControlButton(
                icon = if (staticState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (staticState.isPlaying) "暂停" else "播放",
                size = 116.dp,
                backgroundColor = QQMusicGreen,
                contentColor = Color.White,
                onClick = onPlayPause
            )
            CarControlButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "下一首",
                size = 84.dp,
                onClick = onNext
            )
        }
        CarControlButton(
            icon = if (staticState.currentTrack?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (staticState.currentTrack?.isFavorite == true) "取消收藏" else "收藏",
            size = 72.dp,
            backgroundColor = if (staticState.currentTrack?.isFavorite == true) Color(0xFFE94B6A) else Color.White.copy(alpha = 0.10f),
            contentColor = if (staticState.currentTrack?.isFavorite == true) Color.White else Color.White.copy(alpha = 0.86f),
            onClick = onFavorite
        )
    }
}

@Composable
private fun CarControlButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.10f),
    contentColor: Color = Color.White.copy(alpha = 0.90f),
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .glassSurface(shape = RoundedCornerShape(999.dp), pressScale = true)
            .clip(CircleShape)
            .background(backgroundColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}
