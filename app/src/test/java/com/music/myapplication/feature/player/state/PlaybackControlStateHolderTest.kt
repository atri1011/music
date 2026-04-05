package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.DispatchersProvider
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlaybackSnapshot
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.media.player.QueueManager
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.session.MediaControllerConnector
import com.music.myapplication.media.state.PlaybackStateStore
import com.music.myapplication.core.download.DownloadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControlStateHolderTest {

    @Test
    fun `downloadTrack publishes permission request when legacy storage permission is missing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val track = testTrack()
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(false)
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                attachScope(env.holder, actionScope)
                env.holder.downloadTrack(track)
                advanceUntilIdle()

                val actionState = env.holder.trackActionState.value
                assertEquals(track, actionState.downloadPermissionTrack)
                assertEquals(1L, actionState.downloadPermissionRequestId)
                assertNull(actionState.errorMessage)
                coVerify(exactly = 0) { env.resolver.resolve(any(), any()) }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `granted permission retries pending download automatically`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val track = testTrack()
            val resolvedTrack = track.copy(playableUrl = "https://example.com/track.mp3", quality = "128k")
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(false, true),
                resolvedPlayback = ResolvedTrackPlayback(resolvedTrack)
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                attachScope(env.holder, actionScope)
                env.holder.downloadTrack(track)
                advanceUntilIdle()
                env.holder.onDownloadPermissionResult(track, granted = true)
                advanceUntilIdle()

                val actionState = env.holder.trackActionState.value
                assertNull(actionState.downloadPermissionTrack)
                assertEquals(0L, actionState.downloadPermissionRequestId)
                coVerify(exactly = 1) { env.resolver.resolve(track, "128k") }
                coVerify(exactly = 1) {
                    env.downloadManager.enqueueDownload(
                        match { it.id == track.id && it.platform == track.platform && it.playableUrl == resolvedTrack.playableUrl },
                        resolvedTrack.playableUrl,
                        "128k"
                    )
                }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `denied permission reports actionable error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val track = testTrack()
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(false)
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                attachScope(env.holder, actionScope)
                env.holder.downloadTrack(track)
                advanceUntilIdle()
                env.holder.onDownloadPermissionResult(track, granted = false)
                advanceUntilIdle()

                val actionState = env.holder.trackActionState.value
                assertNull(actionState.downloadPermissionTrack)
                assertEquals(0L, actionState.downloadPermissionRequestId)
                assertEquals(
                    "未授予公共存储写入权限，请在系统设置里允许存储权限后再下载",
                    actionState.errorMessage
                )
                coVerify(exactly = 0) { env.resolver.resolve(any(), any()) }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `togglePlayPause delegates paused restore to service play command`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val bindScope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val track = testTrack(id = "restored-track")
            val env = createEnvironment(dispatcher = dispatcher, permissionResults = listOf(true))
            env.stateStore.updateTrack(track)
            env.stateStore.updateQueue(listOf(track), 0)
            env.stateStore.updatePosition(7_000L)
            env.stateStore.updateDuration(track.durationMs)
            env.queueManager.setQueue(listOf(track), 0)
            env.holder.bind(bindScope)
            runCurrent()

            env.holder.togglePlayPause()
            runCurrent()

            verify(exactly = 1) { env.connector.play() }
            verify(exactly = 0) { env.connector.hasMediaItem() }
            coVerify(exactly = 0) { env.resolver.resolve(any(), any()) }
        } finally {
            bindScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `bind keeps service restored state instead of overwriting with snapshot fallback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val bindScope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val serviceTrack = testTrack(id = "service-track", title = "来自 Service")
            val snapshotTrack = testTrack(id = "snapshot-track", title = "来自 Snapshot")
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(true),
                savedSnapshot = PlaybackSnapshot(
                    currentTrack = snapshotTrack,
                    queue = listOf(snapshotTrack),
                    currentIndex = 0,
                    positionMs = 1_200L
                )
            )
            env.stateStore.updateTrack(serviceTrack)
            env.stateStore.updateQueue(listOf(serviceTrack), 0)
            env.stateStore.updatePosition(7_000L)
            env.stateStore.updateDuration(serviceTrack.durationMs)
            env.queueManager.setQueue(listOf(serviceTrack), 0)

            env.holder.bind(bindScope)
            runCurrent()

            assertEquals(serviceTrack, env.stateStore.state.value.currentTrack)
            assertEquals(listOf(serviceTrack), env.queueManager.queue)
            assertEquals(7_000L, env.stateStore.state.value.positionMs)
        } finally {
            bindScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `seekTo does not persist snapshot before service publishes updated position`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(true)
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                attachScope(env.holder, actionScope)
                env.stateStore.updatePosition(1_000L)

                env.holder.seekTo(9_000L)
                advanceUntilIdle()

                verify(exactly = 1) { env.connector.seekTo(9_000L) }
                coVerify(exactly = 0) { env.preferences.savePlaybackSnapshot(any(), any()) }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `stopPlayback does not persist snapshot before service publishes stopped state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(true)
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                env.holder.bind(actionScope)
                runCurrent()
                val track = testTrack(playableUrl = "https://example.com/current.mp3")
                env.stateStore.updateTrack(track)
                env.stateStore.updateQueue(listOf(track), 0)
                env.stateStore.updatePosition(3_000L)
                env.stateStore.updateDuration(track.durationMs)
                env.stateStore.updatePlaying(true)

                env.holder.stopPlayback()
                runCurrent()

                verify(exactly = 1) { env.connector.stop() }
                coVerify(exactly = 0) { env.preferences.savePlaybackSnapshot(any(), any()) }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `stopPlayback persists cleared snapshot after service publishes stopped state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(true)
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                env.holder.bind(actionScope)
                runCurrent()
                val track = testTrack(playableUrl = "https://example.com/current.mp3")
                env.stateStore.updateTrack(track)
                env.stateStore.updateQueue(listOf(track), 0)
                env.stateStore.updatePosition(3_000L)
                env.stateStore.updateDuration(track.durationMs)
                env.stateStore.updatePlaying(true)
                every { env.connector.stop() } answers { env.stateStore.reset() }

                env.holder.stopPlayback()
                advanceTimeBy(2_000L)
                runCurrent()

                coVerify(exactly = 1) {
                    env.preferences.savePlaybackSnapshot(
                        match {
                            it.currentTrack == null &&
                                it.queue.isEmpty() &&
                                it.currentIndex == -1 &&
                                it.positionMs == 0L &&
                                it.durationMs == 0L &&
                                !it.isPlaying
                        },
                        null
                    )
                }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `pausePlayback persists recoverable shuffle session alongside playback snapshot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val queueManager = QueueManager().apply {
                setQueue(
                    listOf(
                        testTrack(id = "track-1"),
                        testTrack(id = "track-2"),
                        testTrack(id = "track-3")
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
                preferences = mockk(relaxed = true)
            ).apply {
                setMode(PlaybackMode.SHUFFLE)
            }
            val expectedShuffleSession = requireNotNull(modeManager.buildPersistableShuffleSnapshot())
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(true),
                modeManager = modeManager,
                queueManager = queueManager,
                stateStore = stateStore
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                attachScope(env.holder, actionScope)
                attachPlaybackState(env.holder, env.stateStore.state)
                env.stateStore.updatePlaying(true)

                env.holder.pausePlayback()
                advanceUntilIdle()

                verify(exactly = 1) { env.connector.pause() }
                coVerify(exactly = 1) {
                    env.preferences.savePlaybackSnapshot(
                        match {
                            it.playbackMode == PlaybackMode.SHUFFLE &&
                                it.currentTrack == queueManager.currentTrack &&
                                it.queue == queueManager.queue &&
                                it.currentIndex == queueManager.currentIndex
                        },
                        expectedShuffleSession
                    )
                }
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `source fallback info message is published once for consecutive duplicates`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val track = testTrack()
            val fallbackMessage = "JKAPI 当前歌曲不可用，已自动切到 TuneHub"
            val resolvedTrack = track.copy(playableUrl = "https://example.com/track.mp3", quality = "128k")
            val env = createEnvironment(
                dispatcher = dispatcher,
                permissionResults = listOf(true),
                resolvedPlayback = ResolvedTrackPlayback(
                    track = resolvedTrack,
                    sourceFallbackMessage = fallbackMessage
                )
            )
            val actionScope = CoroutineScope(SupervisorJob() + dispatcher)

            try {
                attachScope(env.holder, actionScope)
                env.holder.playTrack(track, listOf(track), 0)
                advanceUntilIdle()

                val firstState = env.holder.trackActionState.value
                assertEquals(fallbackMessage, firstState.infoMessage)
                assertEquals(1L, firstState.infoId)

                env.holder.clearTrackActionInfo()
                env.holder.playTrack(track, listOf(track), 0)
                advanceUntilIdle()

                val secondState = env.holder.trackActionState.value
                assertNull(secondState.infoMessage)
                assertEquals(1L, secondState.infoId)
            } finally {
                actionScope.cancel()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createEnvironment(
        dispatcher: TestDispatcher,
        permissionResults: List<Boolean>,
        resolvedPlayback: ResolvedTrackPlayback = ResolvedTrackPlayback(
            testTrack(playableUrl = "https://example.com/original.mp3", quality = "128k")
        ),
        savedSnapshot: PlaybackSnapshot? = null,
        modeManager: PlaybackModeManager? = null,
        queueManager: QueueManager? = null,
        stateStore: PlaybackStateStore? = null
    ): TestEnvironment {
        val downloadManager = mockk<DownloadManager>()
        val preferences = mockk<PlayerPreferences>()
        val resolver = mockk<TrackPlaybackResolver>()
        val dispatchers = mockk<DispatchersProvider>()
        val connector = mockk<MediaControllerConnector>(relaxed = true)
        val holderStateStore = stateStore ?: PlaybackStateStore()
        val holderQueueManager = queueManager ?: QueueManager()
        val holderModeManager = modeManager ?: mockk(relaxed = true)
        if (modeManager == null) {
            every { holderModeManager.buildPersistableShuffleSnapshot() } returns null
        }

        coEvery { downloadManager.shouldWaitForUnmeteredNetwork() } returns false
        var permissionCallCount = 0
        every { downloadManager.hasRequiredDownloadPermission() } answers {
            permissionResults.getOrElse(permissionCallCount++) { permissionResults.last() }
        }
        coEvery { downloadManager.enqueueDownload(any(), any(), any()) } returns true

        coEvery { resolver.resolve(any(), any()) } returns Result.Success(resolvedPlayback)
        every { preferences.playbackMode } returns flowOf(PlaybackMode.SEQUENTIAL)
        every { preferences.quality } returns flowOf("128k")
        every { preferences.playbackSpeed } returns flowOf(1f)
        every { preferences.playbackSnapshot } returns flowOf(savedSnapshot)
        coEvery { preferences.savePlaybackSnapshot(any(), any()) } returns Unit

        every { dispatchers.main } returns dispatcher
        every { dispatchers.io } returns dispatcher
        every { dispatchers.default } returns dispatcher

        val holder = PlaybackControlStateHolder(
            stateStore = holderStateStore,
            connector = connector,
            modeManager = holderModeManager,
            queueManager = holderQueueManager,
            localRepo = mockk<LocalLibraryRepository>(relaxed = true),
            preferences = preferences,
            resolver = resolver,
            downloadManager = downloadManager,
            dispatchers = dispatchers
        )

        return TestEnvironment(
            holder = holder,
            downloadManager = downloadManager,
            resolver = resolver,
            preferences = preferences,
            connector = connector,
            queueManager = holderQueueManager,
            stateStore = holderStateStore
        )
    }

    private fun attachScope(holder: PlaybackControlStateHolder, scope: CoroutineScope) {
        val scopeField = PlaybackControlStateHolder::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        scopeField.set(holder, scope)
    }

    private fun attachPlaybackState(holder: PlaybackControlStateHolder, playbackState: Any) {
        val playbackStateField = PlaybackControlStateHolder::class.java.getDeclaredField("playbackState")
        playbackStateField.isAccessible = true
        playbackStateField.set(holder, playbackState)
    }

    private fun testTrack(
        id: String = "track-1",
        title: String = "晴天",
        playableUrl: String = "",
        quality: String = "",
        durationMs: Long = 245_000L
    ) = Track(
        id = id,
        platform = Platform.QQ,
        title = title,
        artist = "周杰伦",
        playableUrl = playableUrl,
        quality = quality,
        durationMs = durationMs
    )

    private data class TestEnvironment(
        val holder: PlaybackControlStateHolder,
        val downloadManager: DownloadManager,
        val resolver: TrackPlaybackResolver,
        val preferences: PlayerPreferences,
        val connector: MediaControllerConnector,
        val queueManager: QueueManager,
        val stateStore: PlaybackStateStore
    )
}
