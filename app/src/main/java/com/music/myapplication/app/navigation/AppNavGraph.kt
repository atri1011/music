package com.music.myapplication.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.music.myapplication.feature.home.HomeScreen
import com.music.myapplication.feature.library.LibraryScreen
import com.music.myapplication.feature.playlist.PlaylistDetailScreen
import com.music.myapplication.feature.search.SearchScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier
    ) {
        composable<Routes.Home> {
            HomeScreen(
                onNavigateToPlaylist = { id, platform, name ->
                    navController.navigate(Routes.PlaylistDetail(id, platform, name))
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.Search) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable<Routes.Search> {
            SearchScreen()
        }
        composable<Routes.Library> {
            LibraryScreen(
                onNavigateToPlaylist = { id, name ->
                    navController.navigate(Routes.PlaylistDetail(id, "local", name))
                }
            )
        }
        composable<Routes.PlaylistDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<Routes.PlaylistDetail>()
            PlaylistDetailScreen(
                playlistId = route.id,
                platform = route.platform,
                title = route.name,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
