package com.music.myapplication.media.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.music.myapplication.core.datastore.EqualizerPreferences
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.feature.player.state.TrackPlaybackResolver
import com.music.myapplication.media.equalizer.EqualizerManager
import com.music.myapplication.media.playback.CacheAwarePlaybackMediaSourceFactory
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.state.PlaybackStateStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MusicPlaybackServiceGaplessTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sequential auto transition advances queue index and preloads following track`() = runTest {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B"),
            testTrack(id = "3", title = "C")
        )
        val harness = createHarness(queue = queue, startIndex = 0, playbackMode = PlaybackMode.SEQUENTIAL)

        harness.invokeScheduleGaplessPreloadForCurrent(autoPlay = true)
        advanceUntilIdle()

        harness.currentMediaItemIndex = 1
        val transitionedMediaItem = MediaItem.Builder()
            .setMediaId(buildPlaybackQueueMediaId(queue[1], queueIndex = 1))
            .build()
        harness.currentMediaItem = transitionedMediaItem

        harness.invokeHandleAutoMediaItemTransition(transitionedMediaItem)
        advanceUntilIdle()

        assertEquals(1, harness.queueManager.currentIndex)
        assertEquals(queue[1], harness.stateStore.state.value.currentTrack)
        assertEquals(
            listOf(
                buildPlaybackQueueMediaId(queue[1], queueIndex = 1),
                buildPlaybackQueueMediaId(queue[2], queueIndex = 2)
            ),
            harness.capturedMediaIds
        )
        verify { harness.exoPlayer.removeMediaItems(0, 1) }
        verify(exactly = 2) { harness.exoPlayer.addMediaSource(any()) }
    }

    @Test
    fun `repeat one auto transition keeps queue index and requeues the same track`() = runTest {
        val queue = listOf(
            testTrack(id = "1", title = "Loop")
        )
        val harness = createHarness(queue = queue, startIndex = 0, playbackMode = PlaybackMode.REPEAT_ONE)

        harness.invokeScheduleGaplessPreloadForCurrent(autoPlay = true)
        advanceUntilIdle()

        harness.currentMediaItemIndex = 1
        val transitionedMediaItem = MediaItem.Builder()
            .setMediaId(buildPlaybackQueueMediaId(queue[0], queueIndex = 0))
            .build()
        harness.currentMediaItem = transitionedMediaItem

        harness.invokeHandleAutoMediaItemTransition(transitionedMediaItem)
        advanceUntilIdle()

        assertEquals(0, harness.queueManager.currentIndex)
        assertEquals(queue[0], harness.stateStore.state.value.currentTrack)
        assertEquals(
            listOf(
                buildPlaybackQueueMediaId(queue[0], queueIndex = 0),
                buildPlaybackQueueMediaId(queue[0], queueIndex = 0)
            ),
            harness.capturedMediaIds
        )
        verify { harness.exoPlayer.removeMediaItems(0, 1) }
        verify(exactly = 2) { harness.exoPlayer.addMediaSource(any()) }
    }

    private fun createHarness(
        queue: List<Track>,
        startIndex: Int,
        playbackMode: PlaybackMode
    ): ServiceHarness {
        val service = MusicPlaybackService()
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
        val playbackMediaSourceFactory = mockk<CacheAwarePlaybackMediaSourceFactory>()
        val localLibraryRepository = mockk<LocalLibraryRepository>(relaxed = true)
        val trackPlaybackResolver = mockk<TrackPlaybackResolver>(relaxed = true)
        val queueManager = QueueManager().apply { setQueue(queue, startIndex) }
        val stateStore = PlaybackStateStore().also {
            it.updateQueue(queueManager.queue, queueManager.currentIndex)
            it.updateTrack(queueManager.currentTrack)
        }
        val modeManager = PlaybackModeManager(
            queueManager = queueManager,
            stateStore = stateStore,
            preferences = mockk<PlayerPreferences>(relaxed = true)
        ).apply {
            setMode(playbackMode)
        }

        val capturedMediaIds = mutableListOf<String>()
        var currentMediaItem: MediaItem? = MediaItem.Builder()
            .setMediaId(buildPlaybackQueueMediaId(queue[startIndex], queueIndex = startIndex))
            .build()
        var currentMediaItemIndex = 0
        var mediaItemCount = 1

        every { exoPlayer.currentMediaItem } answers { currentMediaItem }
        every { exoPlayer.currentMediaItemIndex } answers { currentMediaItemIndex }
        every { exoPlayer.mediaItemCount } answers { mediaItemCount }
        every { exoPlayer.duration } returns queue[startIndex].durationMs
        every { exoPlayer.audioSessionId } returns C.AUDIO_SESSION_ID_UNSET
        every { exoPlayer.removeMediaItems(any(), any()) } answers {
            val fromIndex = firstArg<Int>()
            val toIndex = secondArg<Int>()
            mediaItemCount -= (toIndex - fromIndex)
            if (currentMediaItemIndex >= toIndex) {
                currentMediaItemIndex -= (toIndex - fromIndex)
            } else if (currentMediaItemIndex in fromIndex until toIndex) {
                currentMediaItemIndex = fromIndex
            }
        }
        every { exoPlayer.addMediaSource(any()) } answers {
            mediaItemCount += 1
        }
        every { exoPlayer.volume = any() } just Runs

        every { playbackMediaSourceFactory.create(any(), any()) } answers {
            val mediaItem = secondArg<MediaItem>()
            capturedMediaIds += mediaItem.mediaId
            mockk<MediaSource>()
        }

        service.injectField("exoPlayer", exoPlayer)
        service.injectField("stateStore", stateStore)
        service.injectField("queueManager", queueManager)
        service.injectField("modeManager", modeManager)
        service.injectField("sleepTimer", mockk<SleepTimerStateHolder>(relaxed = true))
        service.injectField("equalizerManager", mockk<EqualizerManager>(relaxed = true))
        service.injectField("equalizerPreferences", mockk<EqualizerPreferences>(relaxed = true))
        service.injectField("playerPreferences", mockk<PlayerPreferences>(relaxed = true))
        service.injectField("trackPlaybackResolver", trackPlaybackResolver)
        service.injectField("localLibraryRepository", localLibraryRepository)
        service.injectField("playbackMediaSourceFactory", playbackMediaSourceFactory)

        return ServiceHarness(
            service = service,
            exoPlayer = exoPlayer,
            queueManager = queueManager,
            stateStore = stateStore,
            capturedMediaIds = capturedMediaIds,
            currentMediaItemAccessor = { currentMediaItem },
            currentMediaItemMutator = { currentMediaItem = it },
            currentMediaItemIndexAccessor = { currentMediaItemIndex },
            currentMediaItemIndexMutator = { currentMediaItemIndex = it }
        )
    }

    private fun testTrack(id: String, title: String) = Track(
        id = id,
        platform = Platform.QQ,
        title = title,
        artist = "歌手",
        durationMs = 180_000L,
        playableUrl = "https://cdn.example.com/$id.mp3"
    )

    private data class ServiceHarness(
        val service: MusicPlaybackService,
        val exoPlayer: ExoPlayer,
        val queueManager: QueueManager,
        val stateStore: PlaybackStateStore,
        val capturedMediaIds: List<String>,
        private val currentMediaItemAccessor: () -> MediaItem?,
        private val currentMediaItemMutator: (MediaItem?) -> Unit,
        private val currentMediaItemIndexAccessor: () -> Int,
        private val currentMediaItemIndexMutator: (Int) -> Unit
    ) {
        var currentMediaItem: MediaItem?
            get() = currentMediaItemAccessor()
            set(value) = currentMediaItemMutator(value)

        var currentMediaItemIndex: Int
            get() = currentMediaItemIndexAccessor()
            set(value) = currentMediaItemIndexMutator(value)

        fun invokeScheduleGaplessPreloadForCurrent(autoPlay: Boolean) {
            invokePrivate(
                service = service,
                methodName = "scheduleGaplessPreloadForCurrent",
                parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
                args = arrayOf(autoPlay)
            )
        }

        fun invokeHandleAutoMediaItemTransition(mediaItem: MediaItem?) {
            invokePrivate(
                service = service,
                methodName = "handleAutoMediaItemTransition",
                parameterTypes = arrayOf(MediaItem::class.java),
                args = arrayOf(mediaItem)
            )
        }
    }

    private fun MusicPlaybackService.injectField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@injectField, value)
        }
    }

}

private fun invokePrivate(
    service: MusicPlaybackService,
    methodName: String,
    parameterTypes: Array<Class<*>>,
    args: Array<Any?>
) {
    service.javaClass.getDeclaredMethod(methodName, *parameterTypes).apply {
        isAccessible = true
        invoke(service, *args)
    }
}
