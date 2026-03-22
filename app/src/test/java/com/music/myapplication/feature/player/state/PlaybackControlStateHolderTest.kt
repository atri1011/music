package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.DispatchersProvider
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.media.player.PlaybackModeManager
import com.music.myapplication.media.session.MediaControllerConnector
import com.music.myapplication.core.download.DownloadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
                resolvedTrack = resolvedTrack
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

    private fun createEnvironment(
        dispatcher: TestDispatcher,
        permissionResults: List<Boolean>,
        resolvedTrack: Track = testTrack(playableUrl = "https://example.com/original.mp3", quality = "128k")
    ): TestEnvironment {
        val downloadManager = mockk<DownloadManager>()
        val preferences = mockk<PlayerPreferences>()
        val resolver = mockk<TrackPlaybackResolver>()
        val dispatchers = mockk<DispatchersProvider>()

        coEvery { downloadManager.shouldWaitForUnmeteredNetwork() } returns false
        var permissionCallCount = 0
        every { downloadManager.hasRequiredDownloadPermission() } answers {
            permissionResults.getOrElse(permissionCallCount++) { permissionResults.last() }
        }
        coEvery { downloadManager.enqueueDownload(any(), any(), any()) } returns true

        coEvery { resolver.resolve(any(), any()) } returns Result.Success(resolvedTrack)

        every { dispatchers.main } returns dispatcher
        every { dispatchers.io } returns dispatcher
        every { dispatchers.default } returns dispatcher

        val holder = PlaybackControlStateHolder(
            stateStore = mockk(relaxed = true),
            connector = mockk<MediaControllerConnector>(relaxed = true),
            modeManager = mockk<PlaybackModeManager>(relaxed = true),
            queueManager = mockk(relaxed = true),
            localRepo = mockk<LocalLibraryRepository>(relaxed = true),
            preferences = preferences,
            resolver = resolver,
            downloadManager = downloadManager,
            dispatchers = dispatchers
        )

        return TestEnvironment(
            holder = holder,
            downloadManager = downloadManager,
            resolver = resolver
        )
    }

    private fun attachScope(holder: PlaybackControlStateHolder, scope: CoroutineScope) {
        val scopeField = PlaybackControlStateHolder::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        scopeField.set(holder, scope)
    }

    private fun testTrack(
        playableUrl: String = "",
        quality: String = ""
    ) = Track(
        id = "track-1",
        platform = Platform.QQ,
        title = "晴天",
        artist = "周杰伦",
        playableUrl = playableUrl,
        quality = quality
    )

    private data class TestEnvironment(
        val holder: PlaybackControlStateHolder,
        val downloadManager: DownloadManager,
        val resolver: TrackPlaybackResolver
    )
}
