package com.music.myapplication.media.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommands
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlaybackSnapshot
import com.music.myapplication.core.datastore.PlaybackShuffleSnapshot
import com.music.myapplication.core.datastore.EqualizerPreferences
import com.music.myapplication.core.datastore.PlayerPreferences
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
import com.music.myapplication.media.session.loadTrackSessionCommand
import com.music.myapplication.media.session.refreshQueueSessionCommand
import com.music.myapplication.media.state.PlaybackStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

    @Test
    fun `shuffle auto transition consumes the preloaded cursor and continues preloading forward`() = runTest {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B"),
            testTrack(id = "3", title = "C")
        )
        val harness = createHarness(queue = queue, startIndex = 0, playbackMode = PlaybackMode.SHUFFLE)

        harness.invokeScheduleGaplessPreloadForCurrent(autoPlay = true)
        advanceUntilIdle()

        val firstPreloadedMediaId = requireNotNull(harness.capturedMediaIds.firstOrNull())
        val preloadedIndex = requireNotNull(playbackQueueIndexFromMediaId(firstPreloadedMediaId))

        harness.currentMediaItemIndex = 1
        val transitionedMediaItem = MediaItem.Builder()
            .setMediaId(firstPreloadedMediaId)
            .build()
        harness.currentMediaItem = transitionedMediaItem

        harness.invokeHandleAutoMediaItemTransition(transitionedMediaItem)
        advanceUntilIdle()

        val expectedFollowUpIndex = queue.indices
            .first { index -> index != 0 && index != preloadedIndex }

        assertEquals(preloadedIndex, harness.queueManager.currentIndex)
        assertEquals(queue[preloadedIndex], harness.stateStore.state.value.currentTrack)
        assertEquals(2, harness.capturedMediaIds.size)
        assertEquals(
            expectedFollowUpIndex,
            playbackQueueIndexFromMediaId(harness.capturedMediaIds.last())
        )
        verify { harness.exoPlayer.removeMediaItems(0, 1) }
        verify(exactly = 2) { harness.exoPlayer.addMediaSource(any()) }
    }

    @Test
    fun `service snapshot restore preserves shuffle cursor for later gapless rebuild`() = runTest {
        val queue = listOf(
            testTrack(id = "1", title = "A"),
            testTrack(id = "2", title = "B"),
            testTrack(id = "3", title = "C"),
            testTrack(id = "4", title = "D")
        )
        val seedQueueManager = QueueManager().apply { setQueue(queue, startIndex = 0) }
        val seedStateStore = PlaybackStateStore().also {
            it.updateQueue(seedQueueManager.queue, seedQueueManager.currentIndex)
            it.updateTrack(seedQueueManager.currentTrack)
        }
        val seedModeManager = PlaybackModeManager(
            queueManager = seedQueueManager,
            stateStore = seedStateStore,
            preferences = mockk<PlayerPreferences>(relaxed = true)
        ).apply {
            setMode(PlaybackMode.SHUFFLE)
        }
        val firstPreview = requireNotNull(seedModeManager.peekNextQueueIndexForGapless())
        seedModeManager.commitAutoTransitionToQueueIndex(firstPreview)
        seedQueueManager.moveToIndex(firstPreview)
        val persistedSession = requireNotNull(seedModeManager.buildPersistableShuffleSnapshot())

        val snapshot = PlaybackSnapshot(
            currentTrack = queue[firstPreview],
            queue = queue,
            currentIndex = firstPreview,
            positionMs = 12_000L,
            shuffleSession = persistedSession
        )
        val preferences = mockk<PlayerPreferences>()
        every { preferences.playbackSnapshot } returns flowOf(snapshot)
        every { preferences.playbackMode } returns flowOf(PlaybackMode.SHUFFLE)

        val service = MusicPlaybackService()
        val queueManager = QueueManager()
        val stateStore = PlaybackStateStore()
        val modeManager = PlaybackModeManager(
            queueManager = queueManager,
            stateStore = stateStore,
            preferences = preferences
        )
        service.injectField("queueManager", queueManager)
        service.injectField("stateStore", stateStore)
        service.injectField("modeManager", modeManager)
        service.injectField("playerPreferences", preferences)

        invokePrivate(
            service = service,
            methodName = "restorePlaybackSnapshotStateIfNeeded",
            parameterTypes = emptyArray(),
            args = emptyArray()
        )

        assertEquals(queue, queueManager.queue)
        assertEquals(firstPreview, queueManager.currentIndex)
        assertEquals(persistedSession, modeManager.buildPersistableShuffleSnapshot())
    }

    @Test
    fun `service snapshot restore drives the first preloaded shuffle item after reconstruction`() = runTest {
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
        val harness = createHarness(
            queue = queue,
            startIndex = 2,
            playbackMode = PlaybackMode.SEQUENTIAL,
            playerPreferences = preferences,
            startPreparedPlayback = false,
            seedPlaybackState = false
        )

        invokePrivate(
            service = harness.service,
            methodName = "restorePlaybackSnapshotStateIfNeeded",
            parameterTypes = emptyArray(),
            args = emptyArray()
        )
        harness.currentMediaItem = MediaItem.Builder()
            .setMediaId(buildPlaybackQueueMediaId(queue[2], queueIndex = 2))
            .build()
        harness.currentMediaItemCount = 1
        harness.invokeScheduleGaplessPreloadForCurrent(autoPlay = true)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, harness.queueManager.currentIndex)
        assertEquals(queue[2], harness.stateStore.state.value.currentTrack)
        assertEquals(
            listOf(
                buildPlaybackQueueMediaId(queue[0], queueIndex = 0)
            ),
            harness.capturedMediaIds
        )
    }

    @Test
    fun `session player play restores persisted shuffle snapshot and preloads next gapless item`() = runTest {
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
        val harness = createHarness(
            queue = queue,
            startIndex = 2,
            playbackMode = PlaybackMode.SEQUENTIAL,
            playerPreferences = preferences,
            startPreparedPlayback = false,
            seedPlaybackState = false
        )

        harness.invokeSessionPlayerPlay()
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, harness.queueManager.currentIndex)
        assertEquals(queue[2], harness.stateStore.state.value.currentTrack)
        assertEquals(12_000L, harness.stateStore.state.value.positionMs)
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
    }

    @Test
    fun `media session controller connect restores shuffle snapshot and play keeps gapless preload aligned`() = runTest {
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
        val harness = createHarness(
            queue = queue,
            startIndex = 2,
            playbackMode = PlaybackMode.SEQUENTIAL,
            playerPreferences = preferences,
            startPreparedPlayback = false,
            seedPlaybackState = false,
            buildMediaSession = true
        )

        val connection = harness.connectController()

        assertNotNull(connection.session)
        assertEquals(2, harness.queueManager.currentIndex)
        assertEquals(queue[2], harness.stateStore.state.value.currentTrack)
        assertEquals(12_000L, harness.stateStore.state.value.positionMs)
        assertEquals(true, connection.sessionCommands.contains(loadTrackSessionCommand))
        assertEquals(true, connection.sessionCommands.contains(refreshQueueSessionCommand))
        assertEquals(true, connection.playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(true, connection.playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))

        harness.playViaMediaSession()
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
    }

    private fun createHarness(
        queue: List<Track>,
        startIndex: Int,
        playbackMode: PlaybackMode,
        playerPreferences: PlayerPreferences = mockk(relaxed = true),
        startPreparedPlayback: Boolean = true,
        seedPlaybackState: Boolean = true,
        buildMediaSession: Boolean = false
    ): ServiceHarness {
        val service = if (buildMediaSession) {
            Robolectric.buildService(MusicPlaybackService::class.java).get()
        } else {
            MusicPlaybackService()
        }
        val exoPlayer = mockk<ExoPlayer>(relaxed = true)
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
        every { exoPlayer.setMediaSource(any<MediaSource>()) } answers {
            val mediaId = requireNotNull(latestBuiltMediaId)
            currentMediaItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .build()
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
        every { exoPlayer.addMediaSource(any()) } answers {
            mediaItemCount += 1
        }
        every { exoPlayer.volume = any() } just Runs
        coEvery { trackPlaybackResolver.resolve(any(), any()) } answers {
            Result.Success(ResolvedTrackPlayback(firstArg<Track>()))
        }

        every { playbackMediaSourceFactory.create(any(), any()) } answers {
            val mediaItem = secondArg<MediaItem>()
            latestBuiltMediaId = mediaItem.mediaId
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
        service.injectField("playerPreferences", playerPreferences)
        service.injectField("trackPlaybackResolver", trackPlaybackResolver)
        service.injectField("localLibraryRepository", localLibraryRepository)
        service.injectField("playbackMediaSourceFactory", playbackMediaSourceFactory)
        service.backgroundDispatcher = UnconfinedTestDispatcher(mainDispatcher.scheduler)
        var mediaSession: MediaLibraryService.MediaLibrarySession? = null
        if (buildMediaSession) {
            mediaSession = MediaLibraryService.MediaLibrarySession.Builder(
                service,
                resolveSessionPlayer(service),
                resolveSessionCallback(service)
            ).build()
            service.injectField("mediaSession", mediaSession)
        }

        return ServiceHarness(
            service = service,
            exoPlayer = exoPlayer,
            localLibraryRepository = localLibraryRepository,
            queueManager = queueManager,
            stateStore = stateStore,
            capturedMediaIds = capturedMediaIds,
            mediaSessionAccessor = { mediaSession },
            currentMediaItemAccessor = { currentMediaItem },
            currentMediaItemMutator = { currentMediaItem = it },
            currentMediaItemIndexAccessor = { currentMediaItemIndex },
            currentMediaItemIndexMutator = { currentMediaItemIndex = it },
            currentMediaItemCountAccessor = { mediaItemCount },
            currentMediaItemCountMutator = { mediaItemCount = it }
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
        val localLibraryRepository: LocalLibraryRepository,
        val queueManager: QueueManager,
        val stateStore: PlaybackStateStore,
        val capturedMediaIds: List<String>,
        private val mediaSessionAccessor: () -> MediaLibraryService.MediaLibrarySession?,
        private val currentMediaItemAccessor: () -> MediaItem?,
        private val currentMediaItemMutator: (MediaItem?) -> Unit,
        private val currentMediaItemIndexAccessor: () -> Int,
        private val currentMediaItemIndexMutator: (Int) -> Unit,
        private val currentMediaItemCountAccessor: () -> Int,
        private val currentMediaItemCountMutator: (Int) -> Unit
    ) {
        var currentMediaItem: MediaItem?
            get() = currentMediaItemAccessor()
            set(value) = currentMediaItemMutator(value)

        var currentMediaItemIndex: Int
            get() = currentMediaItemIndexAccessor()
            set(value) = currentMediaItemIndexMutator(value)

        var currentMediaItemCount: Int
            get() = currentMediaItemCountAccessor()
            set(value) {
                currentMediaItemCountMutator(value)
            }

        val mediaSession: MediaLibraryService.MediaLibrarySession?
            get() = mediaSessionAccessor()

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

        fun invokeSessionPlayerPlay() {
            resolveSessionPlayer(service).play()
        }

        fun connectController(
            controller: MediaSession.ControllerInfo = mockk(relaxed = true)
        ): ControllerConnection {
            val session = requireNotNull(mediaSession ?: service.onGetSession(controller))
            val result = resolveSessionCallback(service).onConnect(session, controller)
            return ControllerConnection(
                session = session,
                sessionCommands = result.availableSessionCommands,
                playerCommands = result.availablePlayerCommands
            )
        }

        fun playViaMediaSession() {
            requireNotNull(mediaSession).player.play()
        }

    }

    private data class ControllerConnection(
        val session: MediaSession,
        val sessionCommands: SessionCommands,
        val playerCommands: Player.Commands
    )

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
): Any? {
    service.javaClass.getDeclaredMethod(methodName, *parameterTypes).apply {
        isAccessible = true
        return invoke(service, *args)
    }
}

private fun resolveSessionPlayer(service: MusicPlaybackService): Player =
    service.javaClass.getDeclaredMethod("getSessionPlayer").apply {
        isAccessible = true
    }.invoke(service) as Player

private fun resolveSessionCallback(
    service: MusicPlaybackService
): MediaLibraryService.MediaLibrarySession.Callback =
    service.javaClass.getDeclaredField("sessionCallback").apply {
        isAccessible = true
    }.get(service) as MediaLibraryService.MediaLibrarySession.Callback
