package com.music.myapplication.feature.library

import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.domain.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadedViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state groups downloading failed and success sections`() = runTest {
        val downloadManager = mockk<DownloadManager>()
        coEvery { downloadManager.reconcileTrackedDownloads() } returns Unit
        every { downloadManager.getAllTracks() } returns flowOf(
            listOf(
                entity(
                    songId = "song-downloading",
                    status = DownloadedTrackEntity.DownloadStatus.DOWNLOADING,
                    progressPercent = 37
                ),
                entity(
                    songId = "song-failed",
                    status = DownloadedTrackEntity.DownloadStatus.FAILED,
                    failureReason = "网络波动"
                ),
                entity(
                    songId = "song-success",
                    status = DownloadedTrackEntity.DownloadStatus.SUCCESS,
                    progressPercent = 100,
                    filePath = "content://media/external/audio/media/88"
                )
            )
        )

        val viewModel = DownloadedViewModel(downloadManager)
        val collectJob = launch { viewModel.state.collect {} }

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.downloading.size)
        assertEquals(37, state.downloading.first().progressPercent)
        assertEquals(1, state.failed.size)
        assertEquals("网络波动", state.failed.first().failureReason)
        assertEquals(1, state.downloaded.size)
        assertEquals("content://media/external/audio/media/88", state.downloaded.first().track.playableUrl)
        coVerify(exactly = 1) { downloadManager.reconcileTrackedDownloads() }
        collectJob.cancel()
    }

    @Test
    fun `cancel and remove delegate to download manager`() = runTest {
        val downloadManager = mockk<DownloadManager>()
        val track = Track(
            id = "song-1",
            platform = com.music.myapplication.domain.model.Platform.QQ,
            title = "晴天",
            artist = "周杰伦"
        )
        coEvery { downloadManager.reconcileTrackedDownloads() } returns Unit
        every { downloadManager.getAllTracks() } returns flowOf(emptyList())
        coEvery { downloadManager.cancelDownload(track.id, track.platform.id) } returns Unit
        coEvery { downloadManager.removeDownloaded(track.id, track.platform.id) } returns Unit

        val viewModel = DownloadedViewModel(downloadManager)
        val collectJob = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.cancelDownload(track)
        viewModel.removeDownloaded(track)
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadManager.cancelDownload(track.id, track.platform.id) }
        coVerify(exactly = 1) { downloadManager.removeDownloaded(track.id, track.platform.id) }
        collectJob.cancel()
    }

    private fun entity(
        songId: String,
        status: String,
        progressPercent: Int = 0,
        failureReason: String = "",
        filePath: String = ""
    ) = DownloadedTrackEntity(
        songId = songId,
        platform = "qq",
        title = "测试歌曲",
        artist = "测试歌手",
        album = "测试专辑",
        quality = "320k",
        progressPercent = progressPercent,
        failureReason = failureReason,
        filePath = filePath,
        downloadStatus = status
    )
}
