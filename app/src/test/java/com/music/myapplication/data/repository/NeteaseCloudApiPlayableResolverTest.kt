package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.data.remote.dto.NeteaseCloudSongUrlItemDto
import com.music.myapplication.data.remote.dto.NeteaseCloudSongUrlResponseDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseCloudApiPlayableResolverTest {

    private val api = mockk<NeteaseCloudApiEnhancedApi>()
    private val preferences = mockk<PlayerPreferences>()
    private val resolver = NeteaseCloudApiPlayableResolver(api, preferences)

    @Test
    fun `NETEASE track with configured base URL - returns playable URL`() = runTest {
        val track = testTrack(Platform.NETEASE)
        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://demo.vercel.app/")
        coEvery {
            api.songUrlV1("https://demo.vercel.app/song/url/v1", "33894312", "lossless")
        } returns NeteaseCloudSongUrlResponseDto(
            code = 200,
            data = listOf(
                NeteaseCloudSongUrlItemDto(
                    id = 33894312,
                    code = 200,
                    url = "http://m8.music.126.net/test.flac"
                )
            )
        )

        val result = resolver.resolve(track, "flac")

        assertTrue(result is Result.Success)
        assertEquals("https://m8.music.126.net/test.flac", (result as Result.Success).data)
    }

    @Test
    fun `blank base URL - returns configuration error`() = runTest {
        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("")

        val result = resolver.resolve(testTrack(Platform.NETEASE), "128k")

        assertTrue(result is Result.Error)
        assertEquals("请先设置网易云增强版接口地址", (result as Result.Error).error.message)
        coVerify(exactly = 0) { api.songUrlV1(any(), any(), any()) }
    }

    @Test
    fun `QQ track - returns unsupported error`() = runTest {
        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://demo.vercel.app/")

        val result = resolver.resolve(testTrack(Platform.QQ), "128k")

        assertTrue(result is Result.Error)
        assertEquals("网易云增强版接口仅支持网易云歌曲", (result as Result.Error).error.message)
        coVerify(exactly = 0) { api.songUrlV1(any(), any(), any()) }
    }

    @Test
    fun `trial response without url - returns parse error`() = runTest {
        val track = testTrack(Platform.NETEASE)
        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://demo.vercel.app/")
        coEvery {
            api.songUrlV1(any(), any(), any())
        } returns NeteaseCloudSongUrlResponseDto(
            code = 200,
            data = listOf(
                NeteaseCloudSongUrlItemDto(
                    id = 33894312,
                    code = 200,
                    url = null,
                    freeTrialInfo = buildJsonObject { }
                )
            )
        )

        val result = resolver.resolve(track, "320k")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.Parse)
        assertEquals("当前歌曲仅返回试听片段，建议提供登录态或切换其他音源", result.error.message)
    }

    private fun testTrack(platform: Platform) = Track(
        id = "33894312",
        platform = platform,
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美"
    )
}
