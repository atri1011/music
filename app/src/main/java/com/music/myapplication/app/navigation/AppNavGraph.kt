package com.music.myapplication.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.music.myapplication.feature.artist.ArtistDetailScreen
import com.music.myapplication.feature.home.HomeScreen
import com.music.myapplication.feature.library.LibraryScreen
import com.music.myapplication.feature.more.MoreScreen
import com.music.myapplication.feature.playlist.PlaylistDetailScreen
import com.music.myapplication.feature.player.PlayerLyricsScreen
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.feature.search.SearchScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(250)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(250)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable<Routes.Home> {
            HomeScreen(
                onNavigateToPlaylist = { id, platform, name, source ->
                    navController.navigate(Routes.PlaylistDetail(id, platform, name, source))
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.Search) {
                        launchSingleTop = true
                    }
                },
                playerViewModel = playerViewModel
            )
        }
        composable<Routes.Search> {
            SearchScreen(
                playerViewModel = playerViewModel,
                onNavigateToArtist = { trackId, platformId, artistName ->
                    navController.navigate(Routes.ArtistDetail(trackId, platformId, artistName))
                }
            )
        }
        composable<Routes.Library> {
            LibraryScreen(
                onNavigateToPlaylist = { id, name ->
                    navController.navigate(Routes.PlaylistDetail(id, "local", name, "local"))
                },
                playerViewModel = playerViewModel
            )
        }
        composable<Routes.More> {
            MoreScreen()
        }

        // Detail page: slide in from right
        composable<Routes.PlaylistDetail>(
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Routes.PlaylistDetail>()
            PlaylistDetailScreen(
                playlistId = route.id,
                platform = route.platform,
                title = route.name,
                source = route.source,
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }

        // Artist detail: slide in from right
        composable<Routes.ArtistDetail>(
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            ArtistDetailScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }

        // Player: slide up from bottom
        composable<Routes.PlayerLyrics>(
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(350))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            PlayerLyricsScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
