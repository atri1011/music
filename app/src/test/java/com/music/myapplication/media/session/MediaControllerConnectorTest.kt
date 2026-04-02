package com.music.myapplication.media.session

import android.content.Context
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.state.PlaybackStateStore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaControllerConnectorTest {

    @Test
    fun `seekTo without connected controller leaves position untouched`() {
        val stateStore = PlaybackStateStore().apply { updatePosition(1_000L) }
        val connector = MediaControllerConnector(
            context = mockk<Context>(relaxed = true),
            stateStore = stateStore,
            queueManager = QueueManager()
        )

        connector.seekTo(9_000L)

        assertEquals(1_000L, stateStore.state.value.positionMs)
    }
}
