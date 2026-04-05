package com.music.myapplication.media.session

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.EqualizerPreferences
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.datastore.PlaybackSnapshot
import com.music.myapplication.core.datastore.PlaybackShuffleSnapshot
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.state.ResolvedTrackPlayback
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.feature.player.state.TrackPlaybackResolver
import com.music.myapplication.media.equalizer.EqualizerManager
import com.music.myapplication.media.playback.CacheAwarePlaybackMediaSourceFactory
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.service.MusicPlaybackService
import com.music.myapplication.media.service.buildPlaybackQueueMediaId
import com.music.myapplication.media.state.PlaybackStateStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaControllerConnectorIntegrationTest {

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
    fun `play queued before async controller connection resolves into service restore path`() = runTest {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B"),
            testTrack(id = "3", title = "C"),
            testTrack(id = "4", title = "D")
        )
        val persistedSession = PlaybackShuffleSnapshot(
            queueKeys = queue.mapIndexed { index, track -> "$index:${track.platform.id}:${track.id}" },
            order = listOf(2, 0, 3, 1)
        )
        val snapshot = PlaybackSnapshot(
            currentTrack = queue[2],
            queue = queue,
            currentIndex = 2,
            positionMs = 12_000L,
            shuffleSession = persistedSession
        )
        val preferences = mockk<PlayerPreferences>()
        every { preferences.playbackSnapshot } returns flowOf(snapshot)
        every { preferences.playbackMode } returns flowOf(PlaybackMode.SHUFFLE)
        val harness = createServiceHarness(
            queue = queue,
            startIndex = 2,
            playbackMode = PlaybackMode.SEQUENTIAL,
            playerPreferences = preferences,
            startPreparedPlayback = false,
            seedPlaybackState = false
        )
        val controllerFactory = HarnessMediaControllerConnectionFactory()
        val connector = MediaControllerConnector(
            context = RuntimeEnvironment.getApplication(),
            stateStore = harness.stateStore,
            queueManager = harness.queueManager,
            controllerConnectionFactory = controllerFactory
        )

        try {
            connector.connect()
            connector.play()
            controllerFactory.connectToService(harness)
            mainDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                listOf(
                    buildPlaybackQueueMediaId(queue[2], queueIndex = 2),
                    buildPlaybackQueueMediaId(queue[0], queueIndex = 0)
                ),
                harness.capturedMediaIds
            )
            verify(exactly = 1) { harness.exoPlayer.play() }
            verify(exactly = 1) { harness.exoPlayer.prepare() }
            verify(exactly = 1) { harness.exoPlayer.seekTo(12_000L) }
            coVerify(exactly = 1) {
                harness.localLibraryRepository.recordRecentPlay(queue[2], positionMs = 12_000L)
            }
        } finally {
            connector.disconnect()
            harness.release()
        }
    }

    @Test
    fun `loadTrack queued before async controller connection resolves into service custom command path`() = runTest {
        val queue = listOf(testTrack(id = "9", title = "Loaded"))
        val harness = createServiceHarness(
            queue = queue,
            startIndex = 0,
            playbackMode = PlaybackMode.SEQUENTIAL,
            startPreparedPlayback = false,
            seedPlaybackState = false
        )
        val controllerFactory = HarnessMediaControllerConnectionFactory()
        val connector = MediaControllerConnector(
            context = RuntimeEnvironment.getApplication(),
            stateStore = harness.stateStore,
            queueManager = harness.queueManager,
            controllerConnectionFactory = controllerFactory
        )

        try {
            connector.connect()
            connector.loadTrack(
                track = queue[0],
                queue = queue,
                index = 0,
                autoPlay = false,
                startPositionMs = 9_000L
            )
            controllerFactory.connectToService(harness)
            mainDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                listOf(buildPlaybackQueueMediaId(queue[0], queueIndex = 0)),
                harness.capturedMediaIds
            )
            verify(exactly = 1) { harness.exoPlayer.prepare() }
            verify(exactly = 1) { harness.exoPlayer.pause() }
            verify(exactly = 1) { harness.exoPlayer.seekTo(9_000L) }
        } finally {
            connector.disconnect()
            harness.release()
        }
    }

    private fun createServiceHarness(
        queue: List<Track>,
        startIndex: Int,
        playbackMode: PlaybackMode,
        playerPreferences: PlayerPreferences = mockk(relaxed = true),
        startPreparedPlayback: Boolean = true,
        seedPlaybackState: Boolean = true
    ): ServiceHarness {
        val service = Robolectric.buildService(MusicPlaybackService::class.java).get()
        val exoPlayer = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        val playbackMediaSourceFactory = mockk<CacheAwarePlaybackMediaSourceFactory>()
        val localLibraryRepository = mockk<LocalLibraryRepository>(relaxed = true)
        val trackPlaybackResolver = mockk<TrackPlaybackResolver>()
        val queueManager = QueueManager().apply {
            if (seedPlaybackState) {
                setQueue(queue, startIndex)
            }
        }
        val stateStore = PlaybackStateStore().also {
            if (seedPlaybackState) {
                it.updateQueue(queueManager.queue, queueManager.currentIndex)
                it.updateTrack(queueManager.currentTrack)
            }
        }
        val modeManager = PlaybackModeManager(
            queueManager = queueManager,
            stateStore = stateStore,
            preferences = playerPreferences
        ).apply {
            setMode(playbackMode)
        }

        val capturedMediaIds = mutableListOf<String>()
        var latestBuiltMediaId: String? = null
        var currentMediaItem: MediaItem? = if (startPreparedPlayback) {
            MediaItem.Builder()
                .setMediaId(buildPlaybackQueueMediaId(queue[startIndex], queueIndex = startIndex))
                .build()
        } else {
            null
        }
        var currentMediaItemIndex = 0
        var mediaItemCount = if (startPreparedPlayback) 1 else 0

        every { exoPlayer.currentMediaItem } answers { currentMediaItem }
        every { exoPlayer.currentMediaItemIndex } answers { currentMediaItemIndex }
        every { exoPlayer.mediaItemCount } answers { mediaItemCount }
        every { exoPlayer.canAdvertiseSession() } returns true
        every { exoPlayer.duration } returns queue[startIndex].durationMs
        every { exoPlayer.audioSessionId } returns C.AUDIO_SESSION_ID_UNSET
        every { exoPlayer.playbackParameters } returns androidx.media3.common.PlaybackParameters.DEFAULT
        every { exoPlayer.setMediaSource(any()) } answers {
            val mediaId = requireNotNull(latestBuiltMediaId)
            currentMediaItem = MediaItem.Builder().setMediaId(mediaId).build()
            currentMediaItemIndex = 0
            mediaItemCount = 1
        }
        every { exoPlayer.prepare() } just Runs
        every { exoPlayer.play() } just Runs
        every { exoPlayer.pause() } just Runs
        every { exoPlayer.seekTo(any<Long>()) } just Runs
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
        every { exoPlayer.addMediaSource(any()) } answers { mediaItemCount += 1 }
        every { exoPlayer.volume = any() } just Runs
        coEvery { trackPlaybackResolver.resolve(any(), any()) } answers {
            Result.Success(ResolvedTrackPlayback(firstArg<Track>()))
        }

        every { playbackMediaSourceFactory.create(any(), any()) } answers {
            val mediaItem = secondArg<MediaItem>()
            latestBuiltMediaId = mediaItem.mediaId
            capturedMediaIds += mediaItem.mediaId
            mockk<androidx.media3.exoplayer.source.MediaSource>()
        }

        service.injectField("exoPlayer", exoPlayer)
        service.injectField("stateStore", stateStore)
        service.injectField("queueManager", queueManager)
        service.injectField("modeManager", modeManager)
        service.injectField("sleepTimer", mockk<SleepTimerStateHolder>(relaxed = true))
        service.injectField("equalizerManager", mockk<EqualizerManager>(relaxed = true))
        service.injectField("equalizerPreferences", mockk<EqualizerPreferences>(relaxed = true))
        service.injectField("playerPreferences", playerPreferences)
        service.injectField("trackPlaybackResolver", trackPlaybackResolver)
        service.injectField("localLibraryRepository", localLibraryRepository)
        service.injectField("playbackMediaSourceFactory", playbackMediaSourceFactory)
        service.backgroundDispatcher = UnconfinedTestDispatcher(mainDispatcher.scheduler)
        val mediaSession = MediaLibraryService.MediaLibrarySession.Builder(
            service,
            resolveSessionPlayer(service),
            resolveSessionCallback(service)
        )
            .setId("connector-harness-${System.nanoTime()}")
            .build()
        service.injectField("mediaSession", mediaSession)

        return ServiceHarness(
            service = service,
            exoPlayer = exoPlayer,
            localLibraryRepository = localLibraryRepository,
            queueManager = queueManager,
            stateStore = stateStore,
            capturedMediaIds = capturedMediaIds,
            mediaSession = mediaSession,
            controllerInfo = mockk(relaxed = true)
        )
    }

    private fun MusicPlaybackService.injectField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@injectField, value)
        }
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
        val exoPlayer: androidx.media3.exoplayer.ExoPlayer,
        val localLibraryRepository: LocalLibraryRepository,
        val queueManager: QueueManager,
        val stateStore: PlaybackStateStore,
        val capturedMediaIds: List<String>,
        val mediaSession: MediaLibraryService.MediaLibrarySession,
        val controllerInfo: MediaSession.ControllerInfo
    ) {
        fun release() {
            mediaSession.release()
        }
    }

    private class HarnessMediaControllerConnectionFactory : MediaControllerConnectionFactory {
        private val controllerFuture = SettableFuture.create<MediaController>()
        private var created = false

        override fun create(context: android.content.Context): ListenableFuture<MediaController> {
            check(!created) { "controller future already created" }
            created = true
            return controllerFuture
        }

        override fun release(future: ListenableFuture<MediaController>) {
            future.cancel(false)
        }

        fun connectToService(harness: ServiceHarness) {
            if (controllerFuture.isDone) return
            controllerFuture.set(buildControllerBridge(harness))
        }

        private fun buildControllerBridge(harness: ServiceHarness): MediaController {
            val controller = mockk<MediaController>(relaxed = true)
            every { controller.play() } answers {
                harness.mediaSession.player.play()
            }
            every { controller.sendCustomCommand(any<SessionCommand>(), any<Bundle>()) } answers {
                resolveSessionCallback(harness.service).onCustomCommand(
                    harness.mediaSession,
                    harness.controllerInfo,
                    firstArg(),
                    secondArg()
                )
            }
            every { controller.release() } just Runs
            return controller
        }
    }
}

private fun resolveSessionPlayer(service: MusicPlaybackService): androidx.media3.common.Player =
    service.javaClass.getDeclaredMethod("getSessionPlayer").apply {
        isAccessible = true
    }.invoke(service) as androidx.media3.common.Player

private fun resolveSessionCallback(
    service: MusicPlaybackService
): MediaLibraryService.MediaLibrarySession.Callback =
    service.javaClass.getDeclaredField("sessionCallback").apply {
        isAccessible = true
    }.get(service) as MediaLibraryService.MediaLibrarySession.Callback
