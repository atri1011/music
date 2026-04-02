package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.AudioSource
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceRouterTest {

    private val preferences = mockk<PlayerPreferences>()
    private val tuneHubResolver = mockk<TuneHubPlayableResolver>()
    private val metingPlayableResolver = mockk<MetingPlayableResolver>()
    private val jkApiResolver = mockk<JkApiPlayableResolver>()
    private val neteaseCloudApiResolver = mockk<NeteaseCloudApiPlayableResolver>()
    private val router = PlaybackSourceRouter(
        preferences,
        tuneHubResolver,
        metingPlayableResolver,
        jkApiResolver,
        neteaseCloudApiResolver
    )

    @Test
    fun `TUNEHUB selected - uses TuneHub resolver`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.audioSource } returns flowOf(AudioSource.TUNEHUB)
        coEvery { tuneHubResolver.resolve(track, "320k") } returns Result.Success("https://example.com/play.mp3")

        val result = router.resolve(track, "320k")

        assertEquals("https://example.com/play.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { jkApiResolver.resolve(any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `METING selected for NETEASE - uses Meting resolver`() = runTest {
        val track = testTrack(Platform.NETEASE)
        every { preferences.audioSource } returns flowOf(AudioSource.METING_BAKA)
        coEvery {
            metingPlayableResolver.resolve(track, "320k")
        } returns Result.Success("https://example.com/meting.mp3")

        val result = router.resolve(track, "320k")

        assertEquals("https://example.com/meting.mp3", (result as Result.Success).data)
        coVerify(exactly = 1) { metingPlayableResolver.resolve(track, "320k") }
        coVerify(exactly = 0) { tuneHubResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { jkApiResolver.resolve(any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `METING selected for KUWO - auto fallback to TuneHub`() = runTest {
        val track = testTrack(Platform.KUWO)
        every { preferences.audioSource } returns flowOf(AudioSource.METING_BAKA)
        coEvery { tuneHubResolver.resolve(track, "128k") } returns Result.Success("https://example.com/kuwo.mp3")

        val result = router.resolve(track, "128k")

        assertEquals("https://example.com/kuwo.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { jkApiResolver.resolve(any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `METING fails - falls back to TuneHub`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.audioSource } returns flowOf(AudioSource.METING_BAKA)
        coEvery {
            metingPlayableResolver.resolve(track, "128k")
        } returns Result.Error(AppError.Parse(message = "Meting 未返回可播放链接"))
        coEvery { tuneHubResolver.resolve(track, "128k") } returns Result.Success("https://example.com/fallback.mp3")

        val result = router.resolve(track, "128k")

        assertEquals("https://example.com/fallback.mp3", (result as Result.Success).data)
        coVerify(exactly = 1) { metingPlayableResolver.resolve(track, "128k") }
        coVerify(exactly = 1) { tuneHubResolver.resolve(track, "128k") }
        coVerify(exactly = 0) { jkApiResolver.resolve(any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `JKAPI selected for KUWO - auto fallback to TuneHub`() = runTest {
        val track = testTrack(Platform.KUWO)
        every { preferences.audioSource } returns flowOf(AudioSource.JKAPI)
        coEvery { tuneHubResolver.resolve(track, "128k") } returns Result.Success("https://example.com/kuwo.mp3")

        val result = router.resolve(track, "128k")

        assertEquals("https://example.com/kuwo.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { jkApiResolver.resolve(any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `JKAPI selected for NETEASE - uses JKAPI resolver`() = runTest {
        val track = testTrack(Platform.NETEASE)
        every { preferences.audioSource } returns flowOf(AudioSource.JKAPI)
        coEvery { jkApiResolver.resolve(track) } returns Result.Success("https://jkapi.com/music.mp3")

        val result = router.resolve(track, "128k")

        assertEquals("https://jkapi.com/music.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { tuneHubResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `JKAPI fails - falls back to TuneHub`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.audioSource } returns flowOf(AudioSource.JKAPI)
        coEvery { jkApiResolver.resolve(track) } returns Result.Error(AppError.Parse(message = "JKAPI 匹配结果不一致"))
        coEvery { tuneHubResolver.resolve(track, "128k") } returns Result.Success("https://example.com/fallback.mp3")

        val result = router.resolve(track, "128k")

        assertEquals("https://example.com/fallback.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 1) { jkApiResolver.resolve(track) }
        coVerify(exactly = 1) { tuneHubResolver.resolve(track, "128k") }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `JKAPI fails and TuneHub also fails - returns TuneHub error`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.audioSource } returns flowOf(AudioSource.JKAPI)
        coEvery { jkApiResolver.resolve(track) } returns Result.Error(AppError.Api(message = "JKAPI error"))
        coEvery { tuneHubResolver.resolve(track, "128k") } returns Result.Error(AppError.Api(message = "TuneHub error"))

        val result = router.resolve(track, "128k")

        assertTrue(result is Result.Error)
        assertEquals("TuneHub error", (result as Result.Error).error.message)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
    }

    @Test
    fun `NETEASE enhanced selected for NETEASE - uses dedicated resolver`() = runTest {
        val track = testTrack(Platform.NETEASE)
        every { preferences.audioSource } returns flowOf(AudioSource.NETEASE_CLOUD_API_ENHANCED)
        coEvery {
            neteaseCloudApiResolver.resolve(track, "320k")
        } returns Result.Success("https://example.com/netease.mp3")

        val result = router.resolve(track, "320k")

        assertEquals("https://example.com/netease.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { tuneHubResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { jkApiResolver.resolve(any()) }
    }

    @Test
    fun `NETEASE enhanced selected for QQ - falls back to TuneHub`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.audioSource } returns flowOf(AudioSource.NETEASE_CLOUD_API_ENHANCED)
        coEvery { tuneHubResolver.resolve(track, "128k") } returns Result.Success("https://example.com/fallback.mp3")

        val result = router.resolve(track, "128k")

        assertEquals("https://example.com/fallback.mp3", (result as Result.Success).data)
        coVerify(exactly = 0) { metingPlayableResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { neteaseCloudApiResolver.resolve(any(), any()) }
        coVerify(exactly = 1) { tuneHubResolver.resolve(track, "128k") }
    }

    private fun testTrack(platform: Platform) = Track(
        id = "track-1",
        platform = platform,
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美"
    )
}
