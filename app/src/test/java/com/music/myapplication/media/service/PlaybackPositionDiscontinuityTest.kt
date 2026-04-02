package com.music.myapplication.media.service

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionDiscontinuityTest {

    @Test
    fun `seek discontinuities publish service position`() {
        assertTrue(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_SEEK))
        assertTrue(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT))
    }

    @Test
    fun `non seek discontinuities do not publish service position`() {
        assertFalse(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION))
        assertFalse(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_SKIP))
        assertFalse(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL))
        assertFalse(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_REMOVE))
        assertFalse(shouldPublishPositionFromDiscontinuity(Player.DISCONTINUITY_REASON_SILENCE_SKIP))
    }
}
