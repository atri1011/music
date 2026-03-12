package com.music.myapplication.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppChromeState(
    val showBottomBar: Boolean,
    val showMiniPlayer: Boolean,
    val showResolvingIndicator: Boolean,
    val snackbarBottomPadding: Dp
)

private data class RouteChromeConfig(
    val showBottomBar: Boolean,
    val supportsMiniPlayer: Boolean,
    val showResolvingIndicator: Boolean,
    val snackbarBottomPaddingWithoutMiniPlayer: Dp,
    val snackbarBottomPaddingWithMiniPlayer: Dp
)

internal fun resolveAppChromeState(
    hasCurrentTrack: Boolean,
    isSearchRoute: Boolean,
    isPlayerLyricsRoute: Boolean,
    isVideoPlayerRoute: Boolean
): AppChromeState {
    val config = when {
        isPlayerLyricsRoute -> FullscreenRouteChromeConfig
        isVideoPlayerRoute -> FullscreenRouteChromeConfig
        isSearchRoute -> SearchRouteChromeConfig
        else -> DefaultRouteChromeConfig
    }

    val showMiniPlayer = hasCurrentTrack && config.supportsMiniPlayer
    return AppChromeState(
        showBottomBar = config.showBottomBar,
        showMiniPlayer = showMiniPlayer,
        showResolvingIndicator = config.showResolvingIndicator,
        snackbarBottomPadding = if (showMiniPlayer) {
            config.snackbarBottomPaddingWithMiniPlayer
        } else {
            config.snackbarBottomPaddingWithoutMiniPlayer
        }
    )
}

private val DefaultRouteChromeConfig = RouteChromeConfig(
    showBottomBar = true,
    supportsMiniPlayer = true,
    showResolvingIndicator = true,
    snackbarBottomPaddingWithoutMiniPlayer = 88.dp,
    snackbarBottomPaddingWithMiniPlayer = 136.dp
)

private val SearchRouteChromeConfig = RouteChromeConfig(
    showBottomBar = false,
    supportsMiniPlayer = true,
    showResolvingIndicator = true,
    snackbarBottomPaddingWithoutMiniPlayer = 24.dp,
    snackbarBottomPaddingWithMiniPlayer = 88.dp
)

private val FullscreenRouteChromeConfig = RouteChromeConfig(
    showBottomBar = false,
    supportsMiniPlayer = false,
    showResolvingIndicator = false,
    snackbarBottomPaddingWithoutMiniPlayer = 24.dp,
    snackbarBottomPaddingWithMiniPlayer = 24.dp
)
