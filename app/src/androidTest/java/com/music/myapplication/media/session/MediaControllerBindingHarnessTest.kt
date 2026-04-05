package com.music.myapplication.media.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.state.PlaybackStateStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MediaControllerBindingHarnessTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var playerPreferences: PlayerPreferences

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var stateStore: PlaybackStateStore

    private lateinit var harness: AndroidTestMediaControllerBindingHarness

    @Before
    fun setUp() {
        hiltRule.inject()
        harness = AndroidTestMediaControllerBindingHarness(
            appContext = ApplicationProvider.getApplicationContext<Context>(),
            playerPreferences = playerPreferences,
            queueManager = queueManager,
            stateStore = stateStore
        )
        harness.reset()
    }

    @After
    fun tearDown() {
        harness.reset()
    }

    @Test
    fun buildAsync_binds_to_music_playback_service_and_exposes_custom_commands() {
        val controller = harness.connect()

        assertNotNull(controller.connectedToken)
        assertEquals("com.music.myapplication", controller.connectedToken!!.packageName)
        assertTrue(controller.availableSessionCommands.contains(loadTrackSessionCommand))
        assertTrue(controller.availableSessionCommands.contains(refreshQueueSessionCommand))
    }

    @Test
    fun refresh_queue_custom_command_updates_service_state() {
        val controller = harness.connect()
        val queue = listOf(
            controllerHarnessTrack(id = "1", title = "Alpha"),
            controllerHarnessTrack(id = "2", title = "Beta")
        )

        val result = controller.sendCustomCommand(
            refreshQueueSessionCommand,
            PlaybackQueueRefreshRequest(queue = queue, index = 1).toCommandExtras()
        ).get(10, TimeUnit.SECONDS)

        assertEquals(androidx.media3.session.SessionResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(queue, queueManager.queue)
        assertEquals(1, queueManager.currentIndex)
        assertEquals(queue[1], stateStore.state.value.currentTrack)
        assertEquals(queue, stateStore.state.value.queue)
        assertEquals(1, stateStore.state.value.currentIndex)
    }
}
