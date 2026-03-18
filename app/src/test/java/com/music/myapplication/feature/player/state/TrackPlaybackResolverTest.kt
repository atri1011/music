package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.AppError
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
import org.junit.Assert.assertSame
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

    @Test
    fun `NETEASE digit id falls back to qq candidate when primary resolving fails`() = runTest {
        val track = testTrack(
            id = "123456",
            platform = Platform.NETEASE,
            album = "",
            coverUrl = "",
            durationMs = 0L
        )
        val candidate = qqCandidateTrack()
        val sourceError = AppError.Parse(message = "解析播放地址失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "320k") } returns Result.Error(sourceError)
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(candidate))
        coEvery { onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, "320k") } returns Result.Success("https://example.com/qq.mp3")

        val result = resolver.resolve(track, "320k")

        assertTrue(result is Result.Success)
        val resolvedTrack = (result as Result.Success).data
        assertEquals(Platform.QQ, resolvedTrack.platform)
        assertEquals(candidate.id, resolvedTrack.id)
        assertEquals(track.title, resolvedTrack.title)
        assertEquals(track.artist, resolvedTrack.artist)
        assertEquals(candidate.album, resolvedTrack.album)
        assertEquals(candidate.coverUrl, resolvedTrack.coverUrl)
        assertEquals(candidate.durationMs, resolvedTrack.durationMs)
        assertEquals("https://example.com/qq.mp3", resolvedTrack.playableUrl)
        assertEquals("320k", resolvedTrack.quality)
        coVerify(exactly = 1) { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) }
        coVerify(exactly = 1) { onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, "320k") }
    }

    @Test
    fun `NETEASE digit id keeps original error when no qq candidate found`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.NETEASE)
        val sourceError = AppError.Parse(message = "主解析失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "128k") } returns Result.Error(sourceError)
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(emptyList())

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Error)
        assertSame(sourceError, (result as Result.Error).error)
        coVerify(exactly = 1) { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) }
        coVerify(exactly = 0) { onlineRepo.resolvePlayableUrl(any(), any(), any()) }
    }

    @Test
    fun `NETEASE digit id returns qq fallback error when playable url resolving fails`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.NETEASE)
        val candidate = qqCandidateTrack(id = "003XYZMID")
        val sourceError = AppError.Parse(message = "主解析失败")
        val fallbackError = AppError.Api(message = "QQ 解析失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "128k") } returns Result.Error(sourceError)
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(candidate))
        coEvery { onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, "128k") } returns Result.Error(fallbackError)

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Error)
        assertSame(fallbackError, (result as Result.Error).error)
        coVerify(exactly = 1) { onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, "128k") }
    }

    @Test
    fun `NETEASE with non digit id does not trigger qq fallback`() = runTest {
        val track = testTrack(id = "netease-mid", platform = Platform.NETEASE)
        val sourceError = AppError.Parse(message = "主解析失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "128k") } returns Result.Error(sourceError)

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Error)
        assertSame(sourceError, (result as Result.Error).error)
        coVerify(exactly = 0) { onlineRepo.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { onlineRepo.resolvePlayableUrl(any(), any(), any()) }
    }

    @Test
    fun `NETEASE with missing title or artist does not trigger qq fallback`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.NETEASE, artist = "")
        val sourceError = AppError.Parse(message = "主解析失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "128k") } returns Result.Error(sourceError)

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Error)
        assertSame(sourceError, (result as Result.Error).error)
        coVerify(exactly = 0) { onlineRepo.search(any(), any(), any(), any()) }
        coVerify(exactly = 0) { onlineRepo.resolvePlayableUrl(any(), any(), any()) }
    }

    @Test
    fun `QQ digit id fallback remains working`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.QQ)
        val candidate = qqCandidateTrack(id = "004ABCQqMID")
        val sourceError = AppError.Parse(message = "主解析失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.resolve(track, "128k") } returns Result.Error(sourceError)
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(candidate))
        coEvery { onlineRepo.resolvePlayableUrl(Platform.QQ, candidate.id, "128k") } returns Result.Success("https://example.com/qq-128.mp3")

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolvedTrack = (result as Result.Success).data
        assertEquals(Platform.QQ, resolvedTrack.platform)
        assertEquals(candidate.id, resolvedTrack.id)
        assertEquals("https://example.com/qq-128.mp3", resolvedTrack.playableUrl)
    }

    private fun testTrack(
        id: String = "song-1",
        platform: Platform = Platform.QQ,
        title: String = "晴天",
        artist: String = "周杰伦",
        album: String = "叶惠美",
        coverUrl: String = "",
        durationMs: Long = 0L,
        playableUrl: String = ""
    ) = Track(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationMs = durationMs,
        playableUrl = playableUrl
    )

    private fun qqCandidateTrack(
        id: String = "004Z8Ihr0JIu5s",
        title: String = "晴天",
        artist: String = "周杰伦",
        album: String = "候选专辑",
        coverUrl: String = "https://example.com/cover.jpg",
        durationMs: Long = 258000L
    ) = Track(
        id = id,
        platform = Platform.QQ,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationMs = durationMs
    )
}
