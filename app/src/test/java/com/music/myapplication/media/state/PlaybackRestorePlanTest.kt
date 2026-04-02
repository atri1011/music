package com.music.myapplication.media.state

import com.music.myapplication.core.datastore.PlaybackSnapshot
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.player.QueueManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRestorePlanTest {

    @Test
    fun `snapshot restore plan falls back to current track position in queue`() {
        val currentTrack = testTrack(id = "current-track")
        val earlierTrack = testTrack(id = "earlier-track")

        val plan = buildPlaybackRestorePlan(
            PlaybackSnapshot(
                currentTrack = currentTrack,
                queue = listOf(earlierTrack, currentTrack),
                currentIndex = 9,
                positionMs = 4_200L
            )
        )

        requireNotNull(plan)
        assertEquals(currentTrack, plan.track)
        assertEquals(listOf(earlierTrack, currentTrack), plan.queue)
        assertEquals(1, plan.index)
        assertEquals(4_200L, plan.positionMs)
    }

    @Test
    fun `state restore plan uses current track when queue is empty`() {
        val currentTrack = testTrack(id = "only-track")

        val plan = buildPlaybackRestorePlan(
            currentTrack = currentTrack,
            queue = emptyList(),
            currentIndex = -1,
            positionMs = 800L
        )

        requireNotNull(plan)
        assertEquals(currentTrack, plan.track)
        assertEquals(listOf(currentTrack), plan.queue)
        assertEquals(0, plan.index)
        assertEquals(800L, plan.positionMs)
    }

    @Test
    fun `apply restore plan syncs queue manager and state store`() {
        val firstTrack = testTrack(id = "queue-1")
        val currentTrack = testTrack(id = "queue-2", durationMs = 180_000L)
        val queueManager = QueueManager()
        val stateStore = PlaybackStateStore()

        applyPlaybackRestorePlan(
            PlaybackRestorePlan(
                track = currentTrack,
                queue = listOf(firstTrack, currentTrack),
                index = 1,
                positionMs = 19_000L
            ),
            queueManager = queueManager,
            stateStore = stateStore
        )

        assertEquals(listOf(firstTrack, currentTrack), queueManager.queue)
        assertEquals(1, queueManager.currentIndex)
        assertEquals(currentTrack, stateStore.state.value.currentTrack)
        assertEquals(listOf(firstTrack, currentTrack), stateStore.state.value.queue)
        assertEquals(1, stateStore.state.value.currentIndex)
        assertEquals(19_000L, stateStore.state.value.positionMs)
        assertEquals(180_000L, stateStore.state.value.durationMs)
        assertEquals(false, stateStore.state.value.isPlaying)
    }

    @Test
    fun `restore plan is null when there is no track information`() {
        assertNull(buildPlaybackRestorePlan(snapshot = null))
        assertNull(
            buildPlaybackRestorePlan(
                currentTrack = null,
                queue = emptyList(),
                currentIndex = -1,
                positionMs = 0L
            )
        )
    }

    private fun testTrack(
        id: String,
        durationMs: Long = 245_000L
    ) = Track(
        id = id,
        platform = Platform.QQ,
        title = "晴天",
        artist = "周杰伦",
        durationMs = durationMs
    )
}
