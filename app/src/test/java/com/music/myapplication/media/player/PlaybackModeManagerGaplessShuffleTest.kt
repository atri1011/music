package com.music.myapplication.media.player

import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.state.PlaybackStateStore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackModeManagerGaplessShuffleTest {

    @Test
    fun `shuffle gapless preview stays stable until auto transition commits the session`() {
        val queueManager = QueueManager().apply {
            setQueue(
                listOf(
                    testTrack(id = "1", title = "A"),
                    testTrack(id = "2", title = "B"),
                    testTrack(id = "3", title = "C")
                ),
                startIndex = 0
            )
        }
        val stateStore = PlaybackStateStore().also {
            it.updateQueue(queueManager.queue, queueManager.currentIndex)
            it.updateTrack(queueManager.currentTrack)
        }
        val modeManager = PlaybackModeManager(
            queueManager = queueManager,
            stateStore = stateStore,
            preferences = mockk<PlayerPreferences>(relaxed = true)
        ).apply {
            setMode(PlaybackMode.SHUFFLE)
        }

        val firstPreview = modeManager.invokeNullableInt("peekNextQueueIndexForGapless")
        val secondPreview = modeManager.invokeNullableInt("peekNextQueueIndexForGapless")

        assertNotNull(firstPreview)
        assertEquals(firstPreview, secondPreview)

        modeManager.invokeUnit("commitAutoTransitionToQueueIndex", requireNotNull(firstPreview))
        queueManager.moveToIndex(firstPreview)

        val nextPreview = modeManager.invokeNullableInt("peekNextQueueIndexForGapless")

        assertNotNull(nextPreview)
        assertNotEquals(firstPreview, nextPreview)
        assertEquals(setOf(1, 2), setOf(firstPreview, nextPreview))
    }

    @Test
    fun `persisted shuffle session restores cursor and pending preview after recreation`() {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B"),
            testTrack(id = "3", title = "C"),
            testTrack(id = "4", title = "D")
        )
        val originalQueueManager = QueueManager().apply { setQueue(queue, startIndex = 0) }
        val originalStateStore = PlaybackStateStore().also {
            it.updateQueue(originalQueueManager.queue, originalQueueManager.currentIndex)
            it.updateTrack(originalQueueManager.currentTrack)
        }
        val originalModeManager = PlaybackModeManager(
            queueManager = originalQueueManager,
            stateStore = originalStateStore,
            preferences = mockk<PlayerPreferences>(relaxed = true)
        ).apply {
            setMode(PlaybackMode.SHUFFLE)
        }

        val firstPreview = requireNotNull(originalModeManager.peekNextQueueIndexForGapless())
        originalModeManager.commitAutoTransitionToQueueIndex(firstPreview)
        originalQueueManager.moveToIndex(firstPreview)
        val secondPreview = requireNotNull(originalModeManager.peekNextQueueIndexForGapless())
        val persistedSession = requireNotNull(originalModeManager.buildPersistableShuffleSnapshot())

        val recreatedQueueManager = QueueManager().apply { setQueue(queue, startIndex = firstPreview) }
        val recreatedStateStore = PlaybackStateStore().also {
            it.updateQueue(recreatedQueueManager.queue, recreatedQueueManager.currentIndex)
            it.updateTrack(recreatedQueueManager.currentTrack)
        }
        val recreatedModeManager = PlaybackModeManager(
            queueManager = recreatedQueueManager,
            stateStore = recreatedStateStore,
            preferences = mockk<PlayerPreferences>(relaxed = true)
        )

        recreatedModeManager.restorePersistedShuffleSnapshot(persistedSession)
        recreatedModeManager.setMode(PlaybackMode.SHUFFLE)

        assertEquals(persistedSession, recreatedModeManager.buildPersistableShuffleSnapshot())
        assertEquals(secondPreview, recreatedModeManager.peekNextQueueIndexForGapless())
    }

    private fun testTrack(id: String, title: String) = Track(
        id = id,
        platform = Platform.QQ,
        title = title,
        artist = "歌手"
    )
}

private fun PlaybackModeManager.invokeNullableInt(methodName: String): Int? = runCatching {
    javaClass.declaredMethods.first { it.name.startsWith(methodName) && it.parameterCount == 0 }.apply {
        isAccessible = true
    }.invoke(this) as? Int
}.getOrNull()

private fun PlaybackModeManager.invokeUnit(methodName: String, queueIndex: Int) {
    runCatching {
        javaClass.declaredMethods.first {
            it.name.startsWith(methodName) &&
                it.parameterCount == 1 &&
                it.parameterTypes.single() == Int::class.javaPrimitiveType
        }.apply {
            isAccessible = true
        }.invoke(this, queueIndex)
    }
}
