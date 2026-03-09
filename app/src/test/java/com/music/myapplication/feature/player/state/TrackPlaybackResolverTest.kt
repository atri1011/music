package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.data.repository.PlaybackSourceRouter
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPlaybackResolverTest {

    private val onlineRepo = mockk<OnlineMusicRepository>()
    private val sourceRouter = mockk<PlaybackSourceRouter>()
    private val downloadManager = mockk<DownloadManager>()
    private val resolver = TrackPlaybackResolver(onlineRepo, sourceRouter, downloadManager)

    @Test
    fun `local playable path is preferred over remote resolving`() = runTest {
        val file = kotlin.io.path.createTempFile(suffix = ".mp3").toFile().apply {
            writeText("demo")
            deleteOnExit()
        }
        val track = testTrack(playableUrl = file.absolutePath)
        coEvery { downloadManager.getDownloadedFilePath(any(), any()) } returns null

        val result = resolver.resolve(track, "320k")

        assertTrue(result is Result.Success)
        val resolvedTrack = (result as Result.Success).data
        assertTrue(resolvedTrack.playableUrl.startsWith("file:"))
        assertTrue(resolvedTrack.playableUrl.contains(file.name))
        coVerify(exactly = 0) { sourceRouter.resolve(any(), any()) }
    }

    @Test
    fun `downloaded file path is preferred for remote track`() = runTest {
        val file = kotlin.io.path.createTempFile(suffix = ".mp3").toFile().apply {
            writeText("demo")
            deleteOnExit()
        }
        val track = testTrack()
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns file.absolutePath

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolvedTrack = (result as Result.Success).data
        assertTrue(resolvedTrack.playableUrl.startsWith("file:"))
        assertTrue(resolvedTrack.playableUrl.contains(file.name))
        coVerify(exactly = 0) { sourceRouter.resolve(any(), any()) }
    }

    @Test
    fun `remote resolving still works when no local copy exists`() = runTest {
        val track = testTrack()
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "128k") } returns Result.Success("https://example.com/play.mp3")

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        assertEquals("https://example.com/play.mp3", (result as Result.Success).data.playableUrl)
        coVerify(exactly = 1) { sourceRouter.resolve(track, "128k") }
    }

    private fun testTrack(
        platform: Platform = Platform.QQ,
        playableUrl: String = ""
    ) = Track(
        id = "song-1",
        platform = platform,
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美",
        playableUrl = playableUrl
    )
}
