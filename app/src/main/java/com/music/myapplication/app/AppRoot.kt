package com.music.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.music.myapplication.app.navigation.AppNavGraph
import com.music.myapplication.app.navigation.Routes
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.player.AddTrackToPlaylistSheet
import com.music.myapplication.feature.player.MiniPlayerBar
import com.music.myapplication.feature.player.MiniPlayerUiState
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.feature.player.TrackMoreMenu
import com.music.myapplication.feature.more.LxCustomUpdateAlertViewModel
import com.music.myapplication.feature.update.AppUpdateDialog
import com.music.myapplication.feature.update.AppUpdateViewModel
import com.music.myapplication.ui.theme.AppElevation
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.AppSurfaceTone
import com.music.myapplication.ui.theme.appSurfaceBorderColor
import com.music.myapplication.ui.theme.appSurfaceColor

private val TABLET_NAV_RAIL_MIN_WIDTH = 720.dp

data class BottomNavItem(
    val route: Routes,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val navController = rememberNavController()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val lxAlertViewModel: LxCustomUpdateAlertViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val lxAlert by lxAlertViewModel.state.collectAsStateWithLifecycle()
    val miniPlayerState by playerViewModel.miniPlayerState.collectAsStateWithLifecycle()
    val trackActionState by playerViewModel.trackActionState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadPermission = remember {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            null
        }
    }
    val notificationPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    }
    var pendingPermissionTrack by remember { mutableStateOf<Track?>(null) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val track = pendingPermissionTrack
        pendingPermissionTrack = null
        if (track != null) {
            playerViewModel.onDownloadPermissionResult(track, granted)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasRequestedNotificationPermission = true
    }
    val hasNotificationPermission = notificationPermission == null ||
        ContextCompat.checkSelfPermission(
            context,
            notificationPermission
        ) == PackageManager.PERMISSION_GRANTED

    val hasCurrentTrack = miniPlayerState.currentTrack != null
    val isSearchRoute = navBackStackEntry?.destination?.hasRoute(Routes.Search::class) == true
    val isPlayerLyricsRoute = navBackStackEntry?.destination?.hasRoute(Routes.PlayerLyrics::class) == true
    val isVideoPlayerRoute = navBackStackEntry?.destination?.hasRoute(Routes.VideoPlayer::class) == true
    val chromeState = remember(
        hasCurrentTrack,
        isSearchRoute,
        isPlayerLyricsRoute,
        isVideoPlayerRoute
    ) {
        resolveAppChromeState(
            hasCurrentTrack = hasCurrentTrack,
            isSearchRoute = isSearchRoute,
            isPlayerLyricsRoute = isPlayerLyricsRoute,
            isVideoPlayerRoute = isVideoPlayerRoute
        )
    }

    LaunchedEffect(trackActionState.errorId) {
        val message = trackActionState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        playerViewModel.clearTrackActionError()
    }

    LaunchedEffect(trackActionState.infoId) {
        val message = trackActionState.infoMessage ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        playerViewModel.clearTrackActionInfo()
    }

    LaunchedEffect(trackActionState.downloadPermissionRequestId) {
        val permission = downloadPermission ?: return@LaunchedEffect
        val track = trackActionState.downloadPermissionTrack ?: return@LaunchedEffect
        pendingPermissionTrack = track
        playerViewModel.consumeDownloadPermissionRequest()
        downloadPermissionLauncher.launch(permission)
    }

    LaunchedEffect(
        notificationPermission,
        hasNotificationPermission,
        hasRequestedNotificationPermission,
        miniPlayerState.currentTrack?.id,
        miniPlayerState.currentTrack?.platform?.id
    ) {
        if (!shouldLaunchNotificationPermissionRequest(
                sdkInt = Build.VERSION.SDK_INT,
                notificationPermission = notificationPermission,
                hasPermission = hasNotificationPermission,
                hasRequestedInSession = hasRequestedNotificationPermission,
                hasCurrentTrack = hasCurrentTrack
            )
        ) {
            return@LaunchedEffect
        }
        val permission = notificationPermission ?: return@LaunchedEffect
        hasRequestedNotificationPermission = true
        notificationPermissionLauncher.launch(permission)
    }

    val bottomNavItems = remember {
        listOf(
            BottomNavItem(Routes.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(Routes.Library, "我的", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
            BottomNavItem(Routes.More, "更多", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
        )
    }

    SharedTransitionLayout {
        val sharedTransitionScope = this

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= TABLET_NAV_RAIL_MIN_WIDTH && maxHeight > 480.dp
                val showNavigationRail = useNavigationRail && chromeState.showBottomBar
                val showBottomBar = chromeState.showBottomBar && !showNavigationRail
                val snackbarBottomPadding = chromeState.resolvedSnackbarBottomPadding(showBottomBar)

                Box(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showNavigationRail) {
                            AppNavigationRail(
                                items = bottomNavItems,
                                currentRouteSelected = { route ->
                                    navBackStackEntry?.destination?.hasRoute(route::class) == true
                                },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.weight(1f)) {
                                AppNavGraph(
                                    navController = navController,
                                    playerViewModel = playerViewModel,
                                    sharedTransitionScope = sharedTransitionScope
                                )
                            }

                            AnimatedVisibility(visible = chromeState.showMiniPlayer) {
                                MiniPlayerContainer(
                                    playerViewModel = playerViewModel,
                                    miniPlayerState = miniPlayerState,
                                    showResolvingIndicator = trackActionState.isResolving && chromeState.showResolvingIndicator,
                                    onClick = {
                                        navController.navigate(Routes.PlayerLyrics) { launchSingleTop = true }
                                    },
                                    sharedTransitionScope = sharedTransitionScope,
                                    sharedArtworkVisible = chromeState.showMiniPlayer,
                                    modifier = Modifier.padding(
                                        start = AppSpacing.Small,
                                        end = AppSpacing.Small,
                                        top = AppSpacing.XSmall,
                                        bottom = if (showBottomBar) AppSpacing.Small else AppSpacing.XSmall
                                    )
                                )
                            }

                            if (showBottomBar) {
                                AppBottomNavigationBar(
                                    items = bottomNavItems,
                                    currentRouteSelected = { route ->
                                        navBackStackEntry?.destination?.hasRoute(route::class) == true
                                    },
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = AppSpacing.Medium)
                            .padding(bottom = snackbarBottomPadding)
                    )
                }
            }

            val update = updateState.availableUpdate
            if (updateState.showDialog && update != null) {
                AppUpdateDialog(
                    update = update,
                    actionState = updateState.actionState,
                    canSkipUpdate = updateState.canSkipUpdate,
                    downloadProgressPercent = updateState.downloadProgressPercent,
                    stageMessage = updateState.stageMessage,
                    onPrimaryAction = updateViewModel::onPrimaryAction,
                    onLater = updateViewModel::dismissCurrentUpdate,
                    onOpenFullChangelog = update.fullChangelogUrl?.let { url ->
                        {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }

            lxAlert?.let { alert ->
                AlertDialog(
                    onDismissRequest = lxAlertViewModel::dismiss,
                    title = {
                        Text(
                            text = if (alert.scriptVersion.isBlank()) {
                                "${alert.scriptName} 更新提示"
                            } else {
                                "${alert.scriptName} v${alert.scriptVersion} 更新提示"
                            }
                        )
                    },
                    text = { Text(alert.log) },
                    confirmButton = {
                        alert.updateUrl?.let { url ->
                            TextButton(
                                onClick = {
                                    lxAlertViewModel.dismiss()
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            ) {
                                Text("前往更新")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = lxAlertViewModel::dismiss) {
                            Text("关闭")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavigationBar(
    items: List<BottomNavItem>,
    currentRouteSelected: (Routes) -> Boolean,
    onNavigate: (Routes) -> Unit
) {
    val navShape = RoundedCornerShape(0.dp)
    Surface(
        color = appSurfaceColor(AppSurfaceTone.Plain).copy(alpha = 0.92f),
        tonalElevation = AppElevation.Subtle,
        shadowElevation = AppElevation.Subtle,
        shape = navShape,
        modifier = Modifier
            .border(
                BorderStroke(0.5.dp, appSurfaceBorderColor(AppSurfaceTone.Plain)),
                navShape
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                val selected = currentRouteSelected(item.route)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                            Spacer(modifier = Modifier.size(AppSpacing.XXSmall))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    items: List<BottomNavItem>,
    currentRouteSelected: (Routes) -> Boolean,
    onNavigate: (Routes) -> Unit
) {
    val railShape = RoundedCornerShape(
        topEnd = AppShapes.XLarge,
        bottomEnd = AppShapes.XLarge
    )
    Surface(
        color = appSurfaceColor(AppSurfaceTone.Plain).copy(alpha = 0.97f),
        tonalElevation = AppElevation.Subtle,
        shadowElevation = AppElevation.Low,
        shape = railShape,
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .border(
                BorderStroke(0.5.dp, appSurfaceBorderColor(AppSurfaceTone.Plain)),
                railShape
            )
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxHeight()
        ) {
            items.forEach { item ->
                val selected = currentRouteSelected(item.route)
                NavigationRailItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

private fun AppChromeState.resolvedSnackbarBottomPadding(showBottomBar: Boolean): Dp {
    if (this.showBottomBar && !showBottomBar) {
        return if (showMiniPlayer) 88.dp else 24.dp
    }
    return snackbarBottomPadding
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MiniPlayerContainer(
    playerViewModel: PlayerViewModel,
    miniPlayerState: MiniPlayerUiState,
    showResolvingIndicator: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    sharedArtworkVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val miniProgress by playerViewModel.miniProgressState.collectAsStateWithLifecycle()
    var quickActionTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTargetTrack by remember { mutableStateOf<Track?>(null) }

    MiniPlayerBar(
        track = miniPlayerState.currentTrack,
        isPlaying = miniPlayerState.isPlaying,
        quality = miniPlayerState.quality,
        onPlayPause = playerViewModel::togglePlayPause,
        onPrevious = playerViewModel::skipPrevious,
        onNext = playerViewModel::skipNext,
        onToggleFavorite = playerViewModel::toggleFavorite,
        onClick = onClick,
        onSwipeExpand = onClick,
        onLongPress = { quickActionTrack = miniPlayerState.currentTrack },
        showResolvingIndicator = showResolvingIndicator,
        progressFraction = miniProgress,
        sharedTransitionScope = sharedTransitionScope,
        sharedArtworkVisible = sharedArtworkVisible,
        modifier = modifier
    )

    quickActionTrack?.let { track ->
        TrackMoreMenu(
            onDismiss = { quickActionTrack = null },
            onToggleFavorite = {
                quickActionTrack = null
                playerViewModel.toggleFavorite()
            },
            onAddToPlaylist = {
                quickActionTrack = null
                playlistTargetTrack = track
            },
            onDownload = {
                quickActionTrack = null
                playerViewModel.downloadTrack(track)
            },
            onShare = {
                quickActionTrack = null
                ShareUtils.shareTrack(context, track)
            }
        )
    }

    playlistTargetTrack?.let { track ->
        AddTrackToPlaylistSheet(
            track = track,
            playerViewModel = playerViewModel,
            onDismiss = { playlistTargetTrack = null }
        )
    }
}

internal fun shouldLaunchNotificationPermissionRequest(
    sdkInt: Int,
    notificationPermission: String?,
    hasPermission: Boolean,
    hasRequestedInSession: Boolean,
    hasCurrentTrack: Boolean
): Boolean {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) return false
    if (notificationPermission.isNullOrBlank()) return false
    if (hasPermission) return false
    if (hasRequestedInSession) return false
    return hasCurrentTrack
}
