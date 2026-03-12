package com.music.myapplication.app

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppChromeStateTest {

    @Test
    fun routeChromeStateMatchesCurrentShellBehavior() {
        assertEquals(
            AppChromeState(
                showBottomBar = true,
                showMiniPlayer = true,
                showResolvingIndicator = true,
                snackbarBottomPadding = 136.dp
            ),
            resolveAppChromeState(
                hasCurrentTrack = true,
                isSearchRoute = false,
                isPlayerLyricsRoute = false,
                isVideoPlayerRoute = false
            )
        )

        assertEquals(
            AppChromeState(
                showBottomBar = false,
                showMiniPlayer = true,
                showResolvingIndicator = true,
                snackbarBottomPadding = 88.dp
            ),
            resolveAppChromeState(
                hasCurrentTrack = true,
                isSearchRoute = true,
                isPlayerLyricsRoute = false,
                isVideoPlayerRoute = false
            )
        )

        assertEquals(
            AppChromeState(
                showBottomBar = false,
                showMiniPlayer = false,
                showResolvingIndicator = true,
                snackbarBottomPadding = 24.dp
            ),
            resolveAppChromeState(
                hasCurrentTrack = false,
                isSearchRoute = true,
                isPlayerLyricsRoute = false,
                isVideoPlayerRoute = false
            )
        )

        assertEquals(
            AppChromeState(
                showBottomBar = false,
                showMiniPlayer = false,
                showResolvingIndicator = false,
                snackbarBottomPadding = 24.dp
            ),
            resolveAppChromeState(
                hasCurrentTrack = true,
                isSearchRoute = false,
                isPlayerLyricsRoute = true,
                isVideoPlayerRoute = false
            )
        )

        assertEquals(
            AppChromeState(
                showBottomBar = false,
                showMiniPlayer = false,
                showResolvingIndicator = false,
                snackbarBottomPadding = 24.dp
            ),
            resolveAppChromeState(
                hasCurrentTrack = true,
                isSearchRoute = false,
                isPlayerLyricsRoute = false,
                isVideoPlayerRoute = true
            )
        )
    }
}
