package com.music.myapplication.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.music.myapplication.app.navigation.AppNavGraph
import com.music.myapplication.app.navigation.Routes
import com.music.myapplication.feature.player.MiniPlayerBar
import com.music.myapplication.feature.player.MiniPlayerUiState
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.feature.update.AppUpdateDialog
import com.music.myapplication.feature.update.AppUpdateViewModel
import com.music.myapplication.ui.theme.AppElevation
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.AppSurfaceTone
import com.music.myapplication.ui.theme.appSurfaceBorderColor
import com.music.myapplication.ui.theme.appSurfaceColor

data class BottomNavItem(
    val route: Routes,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AppRoot(
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val miniPlayerState by playerViewModel.miniPlayerState.collectAsStateWithLifecycle()
    val trackActionState by playerViewModel.trackActionState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    val bottomNavItems = remember {
        listOf(
            BottomNavItem(Routes.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(Routes.Library, "我的", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
            BottomNavItem(Routes.More, "更多", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AppNavGraph(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }

                if (chromeState.showMiniPlayer) {
                    MiniPlayerContainer(
                        playerViewModel = playerViewModel,
                        miniPlayerState = miniPlayerState,
                        showResolvingIndicator = trackActionState.isResolving && chromeState.showResolvingIndicator,
                        onClick = {
                            navController.navigate(Routes.PlayerLyrics) { launchSingleTop = true }
                        },
                        modifier = Modifier.padding(
                            start = AppSpacing.Small,
                            end = AppSpacing.Small,
                            top = AppSpacing.XSmall,
                            bottom = if (chromeState.showBottomBar) AppSpacing.Small else AppSpacing.XSmall
                        )
                    )
                }

                if (chromeState.showBottomBar) {
                    val navShape = RoundedCornerShape(
                        topStart = AppShapes.XLarge,
                        topEnd = AppShapes.XLarge
                    )
                    Surface(
                        color = appSurfaceColor(AppSurfaceTone.Plain).copy(alpha = 0.97f),
                        tonalElevation = AppElevation.Subtle,
                        shadowElevation = AppElevation.Low,
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
                            bottomNavItems.forEach { item ->
                                val selected = navBackStackEntry?.destination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
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
                                    colors = NavigationBarItemDefaults.colors(
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
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = AppSpacing.Medium)
                    .padding(bottom = chromeState.snackbarBottomPadding)
            )

            val update = updateState.availableUpdate
            if (updateState.showDialog && update != null) {
                AppUpdateDialog(
                    update = update,
                    actionState = updateState.actionState,
                    canSkipUpdate = updateState.canSkipUpdate,
                    downloadProgressPercent = updateState.downloadProgressPercent,
                    stageMessage = updateState.stageMessage,
                    onPrimaryAction = updateViewModel::onPrimaryAction,
                    onLater = updateViewModel::dismissCurrentUpdate
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerContainer(
    playerViewModel: PlayerViewModel,
    miniPlayerState: MiniPlayerUiState,
    showResolvingIndicator: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val miniProgress by playerViewModel.miniProgressState.collectAsStateWithLifecycle()
    MiniPlayerBar(
        track = miniPlayerState.currentTrack,
        isPlaying = miniPlayerState.isPlaying,
        quality = miniPlayerState.quality,
        onPlayPause = playerViewModel::togglePlayPause,
        onNext = playerViewModel::skipNext,
        onToggleFavorite = playerViewModel::toggleFavorite,
        onClick = onClick,
        showResolvingIndicator = showResolvingIndicator,
        progressFraction = miniProgress,
        modifier = modifier
    )
}
