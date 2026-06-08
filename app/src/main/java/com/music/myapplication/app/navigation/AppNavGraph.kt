package com.music.myapplication.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import com.music.myapplication.feature.alarm.PlaybackAlarmScreen
import com.music.myapplication.feature.car.CarModeScreen
import com.music.myapplication.feature.album.AlbumDetailScreen
import com.music.myapplication.feature.artist.ArtistDetailScreen
import com.music.myapplication.feature.ecosystem.EcosystemExpansionScreen
import com.music.myapplication.feature.home.HomeScreen
import com.music.myapplication.feature.library.DownloadedScreen
import com.music.myapplication.feature.library.LibraryScreen
import com.music.myapplication.feature.library.LocalMusicScreen
import com.music.myapplication.feature.library.MusicYearReportScreen
import com.music.myapplication.feature.library.PlayHistoryScreen
import com.music.myapplication.feature.library.PlayRankingScreen
import com.music.myapplication.feature.more.LxSourcesScreen
import com.music.myapplication.feature.more.MoreScreen
import com.music.myapplication.feature.more.audiosource.AudioSourceManagementScreen
import com.music.myapplication.feature.playlist.PlaylistDetailScreen
import com.music.myapplication.feature.player.EqualizerScreen
import com.music.myapplication.feature.player.PlayerLyricsScreen
import com.music.myapplication.feature.player.PlayerViewModel
import com.music.myapplication.feature.player.VideoPlayerScreen
import com.music.myapplication.feature.search.SearchScreen

private const val DetailEnterMillis = 350
private const val DetailFadeInMillis = 300
private const val DetailExitMillis = 200
private const val DetailPopEnterMillis = 250
private const val DetailPopSlideMillis = 300
private const val DetailPopFadeMillis = 250

private fun rootEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { it / 20 })

private fun rootExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it / 20 })

private fun rootPopEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { -it / 20 })

private fun rootPopExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 20 })

private fun detailEnterTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(DetailEnterMillis)
    ) + fadeIn(animationSpec = tween(DetailFadeInMillis))

private fun detailExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(DetailExitMillis))

private fun detailPopEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(DetailPopEnterMillis))

private fun detailPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(DetailPopSlideMillis)
    ) + fadeOut(animationSpec = tween(DetailPopFadeMillis))

private fun videoEnterTransition(): EnterTransition =
    slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350)
    ) + fadeIn(animationSpec = tween(300))

private fun videoPopExitTransition(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { it / 8 },
        animationSpec = tween(280)
    ) + fadeOut(animationSpec = tween(220))

private fun playerEnterTransition(): EnterTransition =
    slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(400)
    ) + fadeIn(animationSpec = tween(350))

private fun playerPopExitTransition(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(350)
    ) + fadeOut(animationSpec = tween(300))

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = modifier,
        enterTransition = { rootEnterTransition() },
        exitTransition = { rootExitTransition() },
        popEnterTransition = { rootPopEnterTransition() },
        popExitTransition = { rootPopExitTransition() }
    ) {
        composable<Routes.Home> {
            HomeScreen(
                onNavigateToPlaylist = { id, platform, name, source ->
                    navController.navigate(Routes.PlaylistDetail(id, platform, name, source))
                },
                onNavigateToArtist = { artistId, platformId, artistName ->
                    navController.navigate(Routes.ArtistDetail(artistId, platformId, artistName))
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
                },
                onNavigateToAlbum = { albumId, platformId, albumName, artistName, coverUrl ->
                    navController.navigate(Routes.AlbumDetail(albumId, platformId, albumName, artistName, coverUrl))
                },
                onNavigateToPlaylist = { id, platformId, name ->
                    navController.navigate(Routes.PlaylistDetail(id, platformId, name, "playlist"))
                }
            )
        }
        composable<Routes.Library> {
            LibraryScreen(
                onNavigateToPlaylist = { id, name ->
                    navController.navigate(Routes.PlaylistDetail(id, "local", name, "local"))
                },
                onNavigateToFavorites = {
                    navController.navigate(
                        Routes.PlaylistDetail(
                            id = "favorites",
                            platform = "local",
                            name = "收藏",
                            source = "favorites"
                        )
                    )
                },
                onNavigateToPlayRanking = {
                    navController.navigate(Routes.PlayRanking)
                },
                onNavigateToPlayHistory = {
                    navController.navigate(Routes.PlayHistory)
                },
                onNavigateToMusicYearReport = {
                    navController.navigate(Routes.MusicYearReport)
                },
                onNavigateToDownloaded = {
                    navController.navigate(Routes.Downloaded)
                },
                onNavigateToLocalMusic = {
                    navController.navigate(Routes.LocalMusic)
                },
                onNavigateToSmartPlaylist = { id, name ->
                    navController.navigate(Routes.PlaylistDetail(id, "local", name, "smart"))
                }
            )
        }
        composable<Routes.More> {
            MoreScreen(
                onNavigateToLxSources = {
                    navController.navigate(Routes.LxSources)
                },
                onNavigateToAudioSourceManagement = {
                    navController.navigate(Routes.AudioSourceManagement)
                },
                onNavigateToCarMode = {
                    navController.navigate(Routes.CarMode)
                },
                onNavigateToPlaybackAlarm = {
                    navController.navigate(Routes.PlaybackAlarm)
                }
            )
        }
        composable<Routes.PlaybackAlarm>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            PlaybackAlarmScreen(onBack = { navController.popBackStack() })
        }
        composable<Routes.CarMode>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            CarModeScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.Home) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable<Routes.LxSources>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            LxSourcesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Routes.AudioSourceManagement>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            AudioSourceManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLxSources = { navController.navigate(Routes.LxSources) }
            )
        }

        // Downloaded: slide in from right
        composable<Routes.Downloaded>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            DownloadedScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSearch = {
                    navController.navigate(Routes.Search) {
                        launchSingleTop = true
                    }
                },
                playerViewModel = playerViewModel
            )
        }

        composable<Routes.LocalMusic>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            LocalMusicScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }

        composable<Routes.PlayRanking>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            PlayRankingScreen(
                onBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.Home) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                playerViewModel = playerViewModel
            )
        }

        composable<Routes.PlayHistory>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            PlayHistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.Home) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                playerViewModel = playerViewModel
            )
        }

        composable<Routes.MusicYearReport>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            MusicYearReportScreen(
                onBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.Home) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // Detail page: slide in from right
        composable<Routes.PlaylistDetail>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Routes.PlaylistDetail>()
            PlaylistDetailScreen(
                playlistId = route.id,
                platform = route.platform,
                title = route.name,
                source = route.source,
                onBack = { navController.popBackStack() },
                onNavigateToSearch = {
                    navController.navigate(Routes.Search) {
                        launchSingleTop = true
                    }
                },
                playerViewModel = playerViewModel
            )
        }

        // Artist detail: slide in from right
        composable<Routes.ArtistDetail>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            ArtistDetailScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }

        // Album detail: slide in from right
        composable<Routes.AlbumDetail>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            AlbumDetailScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }

        // Equalizer: slide in from right
        composable<Routes.Equalizer>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            EqualizerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Routes.EcosystemExpansion>(
            enterTransition = { detailEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { detailPopExitTransition() }
        ) {
            EcosystemExpansionScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenVideoPlayer = { track ->
                    navController.navigate(
                        Routes.VideoPlayer(
                            trackId = track.id,
                            platform = track.platform.id,
                            title = track.title,
                            artist = track.artist,
                            coverUrl = track.coverUrl
                        )
                    )
                }
            )
        }

        composable<Routes.VideoPlayer>(
            enterTransition = { videoEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { videoPopExitTransition() }
        ) {
            VideoPlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Player: slide up from bottom
        composable<Routes.PlayerLyrics>(
            enterTransition = { playerEnterTransition() },
            exitTransition = { detailExitTransition() },
            popEnterTransition = { detailPopEnterTransition() },
            popExitTransition = { playerPopExitTransition() }
        ) {
            PlayerLyricsScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToArtist = { artistId, platformId, artistName ->
                    navController.navigate(Routes.ArtistDetail(artistId, platformId, artistName))
                },
                onNavigateToAlbum = { albumId, platformId, albumName, artistName, coverUrl ->
                    navController.navigate(
                        Routes.AlbumDetail(albumId, platformId, albumName, artistName, coverUrl)
                    )
                },
                onNavigateToVideoPlayer = { track ->
                    navController.navigate(
                        Routes.VideoPlayer(
                            trackId = track.id,
                            platform = track.platform.id,
                            title = track.title,
                            artist = track.artist,
                            coverUrl = track.coverUrl
                        )
                    )
                },
                onNavigateToEqualizer = {
                    navController.navigate(Routes.Equalizer)
                },
                sharedTransitionScope = sharedTransitionScope
            )
        }
    }
}
