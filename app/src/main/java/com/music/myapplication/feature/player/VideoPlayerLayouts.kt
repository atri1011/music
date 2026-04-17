package com.music.myapplication.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.music.myapplication.feature.components.CoverImage
import com.music.myapplication.media.video.VideoPlayerManager
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun PortraitVideoLayout(
    player: Player,
    title: String,
    artist: String,
    platformLabel: String,
    coverUrl: String,
    hasPlayableVideo: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        VideoTopBar(
            title = title.ifBlank { "MV" },
            subtitle = artist.ifBlank { "视频播放" },
            onBack = onBack,
            actionIcon = Icons.Default.Fullscreen,
            actionDescription = "全屏横屏播放",
            onAction = onEnterFullscreen
        )
        Spacer(modifier = Modifier.height(18.dp))
        VideoSurface(
            player = player,
            coverUrl = coverUrl,
            hasPlayableVideo = hasPlayableVideo,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRetry = onRetry,
            isFullscreen = false,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        VideoInfoCard(
            title = title,
            artist = artist,
            platformLabel = platformLabel,
            hasPlayableVideo = hasPlayableVideo,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }
}

@Composable
fun FullscreenLandscapeVideoLayout(
    player: Player,
    title: String,
    artist: String,
    coverUrl: String,
    hasPlayableVideo: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        VideoSurface(
            player = player,
            coverUrl = coverUrl,
            hasPlayableVideo = hasPlayableVideo,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRetry = onRetry,
            isFullscreen = true,
            modifier = Modifier.fillMaxSize()
        )

        VideoTopBar(
            title = title.ifBlank { "当前歌曲" },
            subtitle = artist.ifBlank { "MV / 视频页" },
            onBack = onBack,
            actionIcon = Icons.Default.FullscreenExit,
            actionDescription = "退出全屏",
            onAction = onExitFullscreen,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}
