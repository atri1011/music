package com.music.myapplication.media.player

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueManagerTest {

    private val trackA = Track(id = "1", platform = Platform.QQ, title = "A", artist = "歌手")
    private val trackB = Track(id = "2", platform = Platform.QQ, title = "B", artist = "歌手")
    private val trackC = Track(id = "3", platform = Platform.QQ, title = "C", artist = "歌手")
    private val trackD = Track(id = "4", platform = Platform.QQ, title = "D", artist = "歌手")

    @Test
    fun setQueue_withEmptyTracks_resetsCurrentIndex() {
        val queueManager = QueueManager()

        queueManager.setQueue(emptyList())

        assertEquals(-1, queueManager.currentIndex)
        assertNull(queueManager.currentTrack)
    }

    @Test
    fun removeFromQueue_removingLastTrack_setsCurrentIndexToMinusOne() {
        val queueManager = QueueManager()
        queueManager.setQueue(listOf(trackA), startIndex = 0)

        queueManager.removeFromQueue(0)

        assertEquals(-1, queueManager.currentIndex)
        assertNull(queueManager.currentTrack)
    }

    @Test
    fun moveItem_movingCurrentTrack_updatesCurrentIndex() {
        val queueManager = QueueManager()
        queueManager.setQueue(listOf(trackA, trackB, trackC, trackD), startIndex = 1)

        queueManager.moveItem(from = 1, to = 3)

        assertEquals(3, queueManager.currentIndex)
        assertEquals(trackB, queueManager.currentTrack)
    }

    @Test
    fun moveItem_movingTrackAcrossCurrentTrack_shiftsCurrentIndex() {
        val queueManager = QueueManager()
        queueManager.setQueue(listOf(trackA, trackB, trackC, trackD), startIndex = 2)

        queueManager.moveItem(from = 0, to = 3)

        assertEquals(1, queueManager.currentIndex)
        assertEquals(trackC, queueManager.currentTrack)
    }
}
