package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.JkApi
import com.music.myapplication.data.remote.dto.JkApiResponseDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JkApiPlayableResolverTest {

    private val api = mockk<JkApi>()
    private val preferences = mockk<PlayerPreferences>()
    private val resolver = JkApiPlayableResolver(api, preferences)

    @Test
    fun `QQ track - returns playable URL`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.currentJkapiKey } returns "test-key"
        coEvery {
            api.searchMusic("qq", "json", "test-key", "晴天 周杰伦")
        } returns JkApiResponseDto(
            code = 1, msg = "ok",
            name = "晴天", artist = "周杰伦", album = "叶惠美",
            musicUrl = "https://example.com/play.mp3"
        )

        val result = resolver.resolve(track)

        assertTrue(result is Result.Success)
        assertEquals("https://example.com/play.mp3", (result as Result.Success).data)
    }

    @Test
    fun `NETEASE track - maps to wy platform`() = runTest {
        val track = testTrack(Platform.NETEASE)
        every { preferences.currentJkapiKey } returns "test-key"
        coEvery {
            api.searchMusic("wy", "json", "test-key", "晴天 周杰伦")
        } returns JkApiResponseDto(
            code = 1, msg = "ok",
            name = "晴天", artist = "周杰伦",
            musicUrl = "https://example.com/play.mp3"
        )

        val result = resolver.resolve(track)

        assertTrue(result is Result.Success)
        coVerify { api.searchMusic("wy", any(), any(), any()) }
    }

    @Test
    fun `KUWO track - returns unsupported error`() = runTest {
        val track = testTrack(Platform.KUWO)
        every { preferences.currentJkapiKey } returns "test-key"

        val result = resolver.resolve(track)

        assertTrue(result is Result.Error)
        assertEquals("JKAPI 不支持酷我音乐", (result as Result.Error).error.message)
        coVerify(exactly = 0) { api.searchMusic(any(), any(), any(), any()) }
    }

    @Test
    fun `empty API key - returns error`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.currentJkapiKey } returns ""

        val result = resolver.resolve(track)

        assertTrue(result is Result.Error)
        assertEquals("请先设置 JKAPI 密钥", (result as Result.Error).error.message)
    }

    @Test
    fun `response title mismatch - returns parse error`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.currentJkapiKey } returns "test-key"
        coEvery {
            api.searchMusic("qq", "json", "test-key", "晴天 周杰伦")
        } returns JkApiResponseDto(
            code = 1, msg = "ok",
            name = "夜曲", artist = "周杰伦",
            musicUrl = "https://example.com/wrong.mp3"
        )

        val result = resolver.resolve(track)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.Parse)
    }

    @Test
    fun `API returns error code - returns API error`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.currentJkapiKey } returns "test-key"
        coEvery {
            api.searchMusic("qq", "json", "test-key", "晴天 周杰伦")
        } returns JkApiResponseDto(code = 0, msg = "API limit exceeded")

        val result = resolver.resolve(track)

        assertTrue(result is Result.Error)
        assertEquals("API limit exceeded", (result as Result.Error).error.message)
    }

    @Test
    fun `blank response name - treated as match (no validation)`() = runTest {
        val track = testTrack(Platform.QQ)
        every { preferences.currentJkapiKey } returns "test-key"
        coEvery {
            api.searchMusic("qq", "json", "test-key", "晴天 周杰伦")
        } returns JkApiResponseDto(
            code = 1, msg = "ok", name = "",
            musicUrl = "https://example.com/play.mp3"
        )

        val result = resolver.resolve(track)

        assertTrue(result is Result.Success)
    }

    private fun testTrack(platform: Platform) = Track(
        id = "track-1",
        platform = platform,
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美"
    )
}
