package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.database.dao.FavoritesDao
import com.music.myapplication.core.database.dao.PlaylistRemoteMapDao
import com.music.myapplication.core.database.dao.PlaylistSongsDao
import com.music.myapplication.core.database.dao.PlaylistsDao
import com.music.myapplication.core.datastore.NeteaseAccountStore
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.NeteaseAccountSession
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseAccountRepositoryImplTest {

    @Test
    fun refreshLoginStatusKeepsSessionWhenPlaylistProbeSucceeds() = runTest {
        val session = NeteaseAccountSession(
            userId = 9527L,
            nickname = "测试用户",
            cookie = "MUSIC_U=test_cookie",
            lastSyncAt = 123L
        )
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.loginStatus(
                url = "https://music-api.test/login/status",
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns Json.parseToJsonElement(
            """
            {
              "code": 502,
              "message": "Bad Gateway"
            }
            """.trimIndent()
        )
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                limit = 1,
                offset = 0,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns Json.parseToJsonElement(
            """
            {
              "code": 200,
              "playlist": []
            }
            """.trimIndent()
        )

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi
        )

        val result = repository.refreshLoginStatus()

        assertTrue(result is Result.Success)
        assertEquals(session, (result as Result.Success).data)
        coVerify(exactly = 1) { accountStore.saveSession(session) }
        coVerify(exactly = 0) { accountStore.clearSession() }
    }

    @Test
    fun refreshLoginStatusClearsSessionWhenProbeAlsoReportsExpired() = runTest {
        val session = NeteaseAccountSession(
            userId = 9527L,
            nickname = "测试用户",
            cookie = "MUSIC_U=test_cookie"
        )
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.loginStatus(
                url = "https://music-api.test/login/status",
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns Json.parseToJsonElement(
            """
            {
              "code": 502,
              "message": "Bad Gateway"
            }
            """.trimIndent()
        )
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                limit = 1,
                offset = 0,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns Json.parseToJsonElement(
            """
            {
              "code": 301,
              "message": "未登录"
            }
            """.trimIndent()
        )

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi
        )

        val result = repository.refreshLoginStatus()

        assertTrue(result is Result.Success)
        assertEquals(null, (result as Result.Success).data)
        coVerify(exactly = 1) { accountStore.clearSession() }
    }

    @Test
    fun refreshLoginStatusReturnsOriginalErrorWhenProbeCannotRecover() = runTest {
        val session = NeteaseAccountSession(
            userId = 9527L,
            nickname = "测试用户",
            cookie = "MUSIC_U=test_cookie"
        )
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.loginStatus(
                url = "https://music-api.test/login/status",
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } throws IllegalStateException("timeout")
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                limit = 1,
                offset = 0,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns Json.parseToJsonElement(
            """
            {
              "code": 500,
              "message": "server error"
            }
            """.trimIndent()
        )

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi
        )

        val result = repository.refreshLoginStatus()

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.Network)
        coVerify(exactly = 0) { accountStore.saveSession(any()) }
        coVerify(exactly = 0) { accountStore.clearSession() }
    }

    private fun createRepository(
        preferences: PlayerPreferences,
        accountStore: NeteaseAccountStore,
        enhancedApi: NeteaseCloudApiEnhancedApi
    ): NeteaseAccountRepositoryImpl {
        return NeteaseAccountRepositoryImpl(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            tuneHubApi = mockk<TuneHubApi>(relaxed = true),
            onlineRepo = mockk<OnlineMusicRepository>(relaxed = true),
            playlistsDao = mockk<PlaylistsDao>(relaxed = true),
            playlistSongsDao = mockk<PlaylistSongsDao>(relaxed = true),
            playlistRemoteMapDao = mockk<PlaylistRemoteMapDao>(relaxed = true),
            favoritesDao = mockk<FavoritesDao>(relaxed = true)
        )
    }
}
