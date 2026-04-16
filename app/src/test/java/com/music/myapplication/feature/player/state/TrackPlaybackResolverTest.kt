package com.music.myapplication.feature.player.state

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.data.repository.PlaybackSourceResolution
import com.music.myapplication.data.repository.PlaybackSourceRouter
import com.music.myapplication.domain.model.AudioSource
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val resolved = (result as Result.Success).data
        assertTrue(resolved.track.playableUrl.startsWith("file:"))
        assertTrue(resolved.track.playableUrl.contains(file.name))
        assertNull(resolved.sourceFallbackMessage)
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
        val resolved = (result as Result.Success).data
        assertTrue(resolved.track.playableUrl.startsWith("file:"))
        assertTrue(resolved.track.playableUrl.contains(file.name))
        assertNull(resolved.sourceFallbackMessage)
        coVerify(exactly = 0) { sourceRouter.resolve(any(), any()) }
    }

    @Test
    fun `downloaded content uri is preferred for remote track`() = runTest {
        val contentUri = "content://media/external/audio/media/42"
        val track = testTrack(playableUrl = contentUri)
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns contentUri

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolved = (result as Result.Success).data
        assertEquals(contentUri, resolved.track.playableUrl)
        assertNull(resolved.sourceFallbackMessage)
        coVerify(exactly = 0) { sourceRouter.resolve(any(), any()) }
    }

    @Test
    fun `stale downloaded content uri falls back to remote resolving`() = runTest {
        val staleContentUri = "content://media/external/audio/media/404"
        val track = testTrack(playableUrl = staleContentUri)
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.TUNEHUB
        coEvery { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) } returns Result.Success(emptyList())
        coEvery { sourceRouter.resolve(track, "128k", AudioSource.TUNEHUB) } returns Result.Success(
            sourceResolution("https://example.com/play.mp3")
        )

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolved = (result as Result.Success).data
        assertEquals("https://example.com/play.mp3", resolved.track.playableUrl)
        assertNull(resolved.sourceFallbackMessage)
        coVerify(exactly = 1) { sourceRouter.currentRequestedSource() }
        coVerify(exactly = 1) { sourceRouter.resolve(track, "128k", AudioSource.TUNEHUB) }
    }

    @Test
    fun `remote resolving still works when no local copy exists`() = runTest {
        val track = testTrack()
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.TUNEHUB
        coEvery { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) } returns Result.Success(emptyList())
        coEvery { sourceRouter.resolve(track, "128k", AudioSource.TUNEHUB) } returns Result.Success(
            sourceResolution("https://example.com/play.mp3")
        )

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolved = (result as Result.Success).data
        assertEquals("https://example.com/play.mp3", resolved.track.playableUrl)
        assertNull(resolved.sourceFallbackMessage)
        coVerify(exactly = 1) { sourceRouter.currentRequestedSource() }
        coVerify(exactly = 1) { sourceRouter.resolve(track, "128k", AudioSource.TUNEHUB) }
    }

    @Test
    fun `source router fallback message is exposed to player layer`() = runTest {
        val track = testTrack(platform = Platform.NETEASE)
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.JKAPI
        coEvery { sourceRouter.resolve(track, "128k", AudioSource.JKAPI) } returns Result.Success(
            sourceResolution(
                url = "https://example.com/play.mp3",
                requestedSource = AudioSource.JKAPI,
                resolvedSource = AudioSource.TUNEHUB,
                didFallback = true,
                fallbackReason = "JKAPI 当前歌曲不可用，已自动切到 TuneHub"
            )
        )

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolved = (result as Result.Success).data
        assertEquals("https://example.com/play.mp3", resolved.track.playableUrl)
        assertEquals("JKAPI 当前歌曲不可用，已自动切到 TuneHub", resolved.sourceFallbackMessage)
    }

    @Test
    fun `supported platform candidate is preferred over unsupported original track`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.QQ, album = "", coverUrl = "", durationMs = 0L)
        val candidate = candidateTrack(platform = Platform.NETEASE, id = "netease-1", album = "候选专辑", coverUrl = "https://example.com/netease-cover.jpg", durationMs = 233000L)
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.METING_BAKA
        coEvery { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(candidate))
        coEvery { sourceRouter.resolve(candidate, "320k", AudioSource.METING_BAKA) } returns Result.Success(
            sourceResolution(
                url = "https://example.com/netease.mp3",
                requestedSource = AudioSource.METING_BAKA,
                resolvedSource = AudioSource.METING_BAKA
            )
        )

        val result = resolver.resolve(track, "320k")

        assertTrue(result is Result.Success)
        val resolvedTrack = (result as Result.Success).data.track
        assertEquals(Platform.NETEASE, resolvedTrack.platform)
        assertEquals(candidate.id, resolvedTrack.id)
        assertEquals(track.title, resolvedTrack.title)
        assertEquals(track.artist, resolvedTrack.artist)
        assertEquals(candidate.album, resolvedTrack.album)
        assertEquals(candidate.coverUrl, resolvedTrack.coverUrl)
        assertEquals(candidate.durationMs, resolvedTrack.durationMs)
        assertEquals("https://example.com/netease.mp3", resolvedTrack.playableUrl)
        assertEquals("320k", resolvedTrack.quality)
        coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) }
        coVerify(exactly = 1) { sourceRouter.resolve(candidate, "320k", AudioSource.METING_BAKA) }
        coVerify(exactly = 0) { sourceRouter.resolve(track, any(), any()) }
    }

    @Test
    fun `same platform searched candidate is retried after original track fails`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.QQ)
        val candidate = candidateTrack(platform = Platform.QQ, id = "004ABCQqMID")
        val sourceError = AppError.Parse(message = "QQ songid 无法直接解析")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.METING_BAKA
        coEvery { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) } returns Result.Success(emptyList())
        coEvery { sourceRouter.resolve(track, "128k", AudioSource.METING_BAKA) } returns Result.Error(sourceError)
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(candidate))
        coEvery { sourceRouter.resolve(candidate, "128k", AudioSource.METING_BAKA) } returns Result.Success(
            sourceResolution(
                url = "https://example.com/qq-128.mp3",
                requestedSource = AudioSource.METING_BAKA,
                resolvedSource = AudioSource.METING_BAKA
            )
        )

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolvedTrack = (result as Result.Success).data.track
        assertEquals(Platform.QQ, resolvedTrack.platform)
        assertEquals(candidate.id, resolvedTrack.id)
        assertEquals("https://example.com/qq-128.mp3", resolvedTrack.playableUrl)
        coVerifyOrder {
            onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20)
            sourceRouter.resolve(track, "128k", AudioSource.METING_BAKA)
            onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20)
            sourceRouter.resolve(candidate, "128k", AudioSource.METING_BAKA)
        }
    }

    @Test
    fun `unsupported original platform is retried after supported platform candidates miss`() = runTest {
        val track = testTrack(id = "kuwo-1", platform = Platform.KUWO)
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.METING_BAKA
        coEvery { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) } returns Result.Success(emptyList())
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(emptyList())
        coEvery { sourceRouter.resolve(track, "128k", AudioSource.METING_BAKA) } returns Result.Success(
            sourceResolution(
                url = "https://example.com/kuwo.mp3",
                requestedSource = AudioSource.METING_BAKA,
                resolvedSource = AudioSource.TUNEHUB,
                didFallback = true,
                fallbackReason = "Meting (baka.plus) 不支持酷我，已自动切到 TuneHub"
            )
        )

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Success)
        val resolved = (result as Result.Success).data
        assertEquals(Platform.KUWO, resolved.track.platform)
        assertEquals(track.id, resolved.track.id)
        assertEquals("https://example.com/kuwo.mp3", resolved.track.playableUrl)
        assertEquals("Meting (baka.plus) 不支持酷我，已自动切到 TuneHub", resolved.sourceFallbackMessage)
    }

    @Test
    fun `last candidate error is returned when every attempt fails`() = runTest {
        val track = testTrack(id = "123456", platform = Platform.QQ)
        val neteaseCandidate = candidateTrack(platform = Platform.NETEASE, id = "netease-1")
        val qqCandidate = candidateTrack(platform = Platform.QQ, id = "004ABCQqMID")
        val neteaseError = AppError.Api(message = "网易候选解析失败")
        val originalError = AppError.Parse(message = "原 QQ 曲目解析失败")
        val candidateError = AppError.Api(message = "QQ 候选解析失败")
        coEvery { downloadManager.getDownloadedFilePath(track.id, track.platform.id) } returns null
        coEvery { sourceRouter.currentRequestedSource() } returns AudioSource.METING_BAKA
        coEvery { onlineRepo.search(Platform.NETEASE, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(neteaseCandidate))
        coEvery { sourceRouter.resolve(neteaseCandidate, "128k", AudioSource.METING_BAKA) } returns Result.Error(neteaseError)
        coEvery { sourceRouter.resolve(track, "128k", AudioSource.METING_BAKA) } returns Result.Error(originalError)
        coEvery { onlineRepo.search(Platform.QQ, "晴天 周杰伦", 1, 20) } returns Result.Success(listOf(qqCandidate))
        coEvery { sourceRouter.resolve(qqCandidate, "128k", AudioSource.METING_BAKA) } returns Result.Error(candidateError)

        val result = resolver.resolve(track, "128k")

        assertTrue(result is Result.Error)
        assertSame(candidateError, (result as Result.Error).error)
    }

    private fun sourceResolution(
        url: String,
        requestedSource: AudioSource = AudioSource.TUNEHUB,
        resolvedSource: AudioSource = requestedSource,
        didFallback: Boolean = false,
        fallbackReason: String? = null
    ) = PlaybackSourceResolution(
        playableUrl = url,
        requestedSource = requestedSource,
        resolvedSource = resolvedSource,
        didFallback = didFallback,
        fallbackReason = fallbackReason
    )

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

    private fun candidateTrack(
        platform: Platform,
        id: String = "004Z8Ihr0JIu5s",
        title: String = "晴天",
        artist: String = "周杰伦",
        album: String = "候选专辑",
        coverUrl: String = "https://example.com/cover.jpg",
        durationMs: Long = 258000L
    ) = Track(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationMs = durationMs
    )
}
