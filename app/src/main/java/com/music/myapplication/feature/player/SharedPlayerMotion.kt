package com.music.myapplication.feature.player

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.music.myapplication.domain.model.Track

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedTrackArtwork(
    sharedTransitionScope: SharedTransitionScope?,
    track: Track?,
    visible: Boolean
): Modifier {
    val sharedKey = remember(track?.platform?.id, track?.id) {
        track?.let { "player-artwork:${it.platform.id}:${it.id}" }
    }
    if (sharedTransitionScope == null || sharedKey == null) return this

    return with(sharedTransitionScope) {
        this@sharedTrackArtwork.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = sharedKey),
            visible = visible
        )
    }
}
