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
fun VideoPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenMode by rememberSaveable { mutableStateOf(false) }
    val isFullscreenActive = fullscreenMode && isLandscape
    val videoPlayerManager = remember(context) { VideoPlayerManager(context) }
    val player = videoPlayerManager.player
    var playbackError by remember { mutableStateOf<String?>(null) }

    VideoWindowEffect(fullscreenMode = fullscreenMode)

    BackHandler(enabled = fullscreenMode) {
        fullscreenMode = false
    }

    DisposableEffect(player, videoPlayerManager) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName.ifBlank { error.message ?: "视频播放失败" }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            videoPlayerManager.release()
        }
    }

    LaunchedEffect(state.playUrl) {
        playbackError = null
        videoPlayerManager.prepare(state.playUrl)
    }

    val platformLabel = state.platformName.ifBlank { state.platformId }
    val displayError = playbackError ?: state.error

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.coverUrl.isNotBlank()) {
            AsyncImage(
                model = state.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (isFullscreenActive) 28.dp else 44.dp),
                contentScale = ContentScale.Crop,
                alpha = if (isFullscreenActive) 0.18f else 0.28f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (isFullscreenActive) 0.18f else 0.35f),
                            Color.Black.copy(alpha = if (isFullscreenActive) 0.48f else 0.78f),
                            Color.Black
                        )
                    )
                )
        )

        if (isFullscreenActive) {
            FullscreenLandscapeVideoLayout(
                player = player,
                title = state.title,
                artist = state.artist,
                coverUrl = state.coverUrl,
                hasPlayableVideo = state.hasPlayableVideo,
                isLoading = state.isLoading,
                errorMessage = displayError,
                onRetry = viewModel::retry,
                onBack = onBack,
                onExitFullscreen = { fullscreenMode = false }
            )
        } else {
            PortraitVideoLayout(
                player = player,
                title = state.title,
                artist = state.artist,
                platformLabel = platformLabel,
                coverUrl = state.coverUrl,
                hasPlayableVideo = state.hasPlayableVideo,
                isLoading = state.isLoading,
                errorMessage = displayError,
                onRetry = viewModel::retry,
                onBack = onBack,
                onEnterFullscreen = { fullscreenMode = true }
            )
        }
    }
}

@Composable
private fun VideoWindowEffect(fullscreenMode: Boolean) {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return

    DisposableEffect(activity, view, fullscreenMode) {
        val previousOrientation = activity.requestedOrientation
        val controller = WindowCompat.getInsetsController(activity.window, view)

        if (fullscreenMode) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity.requestedOrientation = previousOrientation
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PortraitVideoLayout(
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
            title = "MV / 视频页",
            subtitle = "沉浸式封面 + 独立视频播放器",
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
        Spacer(modifier = Modifier.height(14.dp))
        VideoHintCard(
            hasPlayableVideo = hasPlayableVideo,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }
}

@Composable
private fun FullscreenLandscapeVideoLayout(
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

@Composable
private fun VideoTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoActionButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回"
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        VideoActionButton(
            onClick = onAction,
            icon = actionIcon,
            contentDescription = actionDescription
        )
    }
}

@Composable
private fun VideoActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .glassSurface(shape = RoundedCornerShape(21.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}

@Composable
private fun VideoSurface(
    player: Player,
    coverUrl: String,
    hasPlayableVideo: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (isFullscreen) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                }
            )
    ) {
        if (hasPlayableVideo) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        resizeMode = if (isFullscreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                        controllerShowTimeoutMs = 3_000
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        this.player = player
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = if (isFullscreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            EmptyVideoPoster(
                coverUrl = coverUrl,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onRetry = onRetry
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isFullscreen) 0.10f else 0.16f),
                            Color.Black.copy(alpha = if (isFullscreen) 0.30f else 0.55f)
                        )
                    )
                )
        )

        if (hasPlayableVideo && errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.58f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyVideoPoster(
    coverUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CoverImage(
            url = coverUrl,
            contentDescription = "视频封面",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.32f)
            ) {
                Box(
                    modifier = Modifier.padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when {
                    isLoading -> "MV 地址解析中"
                    errorMessage != null -> "这首歌的 MV 没拿下来"
                    else -> "当前歌曲暂无可播放 MV"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    isLoading -> "先别急，地址回来就直接开播。"
                    errorMessage != null -> errorMessage
                    else -> "页面和独立播放器都就位了，但这首歌还没有可播视频地址。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.74f),
                textAlign = TextAlign.Center
            )
            if (!isLoading && errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun VideoInfoCard(
    title: String,
    artist: String,
    platformLabel: String,
    hasPlayableVideo: Boolean,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.XLarge))
            .padding(18.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f)
        ) {
            Text(
                text = if (platformLabel.isBlank()) "视频剧场" else "来源 $platformLabel",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title.ifBlank { "当前歌曲" },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.ifBlank { "等待歌曲上下文" },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = when {
                hasPlayableVideo -> "点右上角直接切横屏全屏，系统栏会收起来，播放器自己接管控制。"
                isLoading -> "正在按歌曲信息解析 MV 地址，拿到就直接塞给独立播放器。"
                errorMessage != null -> errorMessage
                else -> "当前歌曲暂时没有拿到可播 MV，空态会兜住，不会把页面整得像坏了一样。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun VideoHintCard(
    hasPlayableVideo: Boolean,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Large))
            .padding(18.dp)
    ) {
        Text(
            text = "这版先收口的点",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))
        VideoHintLine("布局", "竖屏保留信息卡片，点全屏后直接切横屏纯视频。")
        Spacer(modifier = Modifier.height(8.dp))
        VideoHintLine("体验", "横屏时隐藏系统栏和底部导航，别让全屏像半屏凑活。")
        Spacer(modifier = Modifier.height(8.dp))
        VideoHintLine(
            "状态",
            when {
                hasPlayableVideo -> "MV 地址已就绪，直接走独立 ExoPlayer。"
                isLoading -> "正在拉取 MV 地址，回来就自动准备播放。"
                errorMessage != null -> "这首歌的 MV 没解析出来，支持手动重试。"
                else -> "当前歌曲还没有可播视频地址。"
            }
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        hasPlayableVideo -> "支持横屏全屏播放"
                        isLoading -> "MV 地址解析中"
                        errorMessage != null -> "解析失败，可重试"
                        else -> "当前歌曲暂无 MV"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun VideoHintLine(label: String, content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.width(44.dp)
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
