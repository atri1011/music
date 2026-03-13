package com.music.myapplication.data.repository

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.database.dao.FavoritesDao
import com.music.myapplication.core.database.dao.PlaylistRemoteMapDao
import com.music.myapplication.core.database.dao.PlaylistSongsDao
import com.music.myapplication.core.database.dao.PlaylistsDao
import com.music.myapplication.core.database.entity.PlaylistEntity
import com.music.myapplication.core.database.entity.PlaylistRemoteMapEntity
import com.music.myapplication.core.database.entity.PlaylistSongEntity
import com.music.myapplication.core.datastore.NeteaseAccountStore
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.NeteaseAccountSession
import com.music.myapplication.domain.model.NeteaseSyncSummary
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
        } returns json(
            """
            {
              "code": 502,
              "message": "Bad Gateway"
            }
            """
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
        } returns json(
            """
            {
              "code": 200,
              "playlist": []
            }
            """
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
        } returns json(
            """
            {
              "code": 502,
              "message": "Bad Gateway"
            }
            """
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
        } returns json(
            """
            {
              "code": 301,
              "message": "未登录"
            }
            """
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
        } returns json(
            """
            {
              "code": 500,
              "message": "server error"
            }
            """
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

    @Test
    fun syncLocalLibrarySkipsPlaylistDetailWhenRemoteSignatureUnchanged() = runTest {
        val session = defaultSession()
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()
        val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
        val playlistsDao = mockk<PlaylistsDao>(relaxed = true)
        val playlistSongsDao = mockk<PlaylistSongsDao>(relaxed = true)
        val playlistRemoteMapDao = mockk<PlaylistRemoteMapDao>(relaxed = true)
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "playlist": [
                {
                  "id": 10001,
                  "name": "测试歌单",
                  "coverImgUrl": "https://example.com/cover.jpg",
                  "trackCount": 2,
                  "updateTime": 1700000000000
                }
              ]
            }
            """
        )
        coEvery {
            playlistRemoteMapDao.getByRemoteSource(
                sourcePlatform = Platform.NETEASE.id,
                sourcePlaylistId = "10001",
                ownerUid = session.userId.toString()
            )
        } returns PlaylistRemoteMapEntity(
            playlistId = "local-1",
            sourcePlatform = Platform.NETEASE.id,
            sourcePlaylistId = "10001",
            ownerUid = session.userId.toString(),
            remoteSignature = "ut=1700000000000|tc=2|n=测试歌单|c=https://example.com/cover.jpg",
            lastSyncedSongSignature = "netease:1|netease:2",
            lastSyncedAt = 1700000000000
        )
        coEvery {
            enhancedApi.likeList(
                url = "https://music-api.test/likelist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "ids": []
            }
            """
        )
        coEvery { favoritesDao.getSongIdsByPlatform(Platform.NETEASE.id) } returns emptyList()

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            onlineRepo = onlineRepo,
            playlistsDao = playlistsDao,
            playlistSongsDao = playlistSongsDao,
            playlistRemoteMapDao = playlistRemoteMapDao,
            favoritesDao = favoritesDao
        )

        val result = repository.syncLocalLibrary()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.syncedPlaylistCount)
        coVerify(exactly = 0) { onlineRepo.getPlaylistDetail(any(), any()) }
        coVerify(exactly = 0) { playlistSongsDao.replacePlaylistSongs(any(), any()) }
    }

    @Test
    fun syncLocalLibraryUpdatesMappedPlaylistWhenRemoteChangedAndLocalUnchanged() = runTest {
        val session = defaultSession()
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()
        val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
        val playlistsDao = mockk<PlaylistsDao>(relaxed = true)
        val playlistSongsDao = mockk<PlaylistSongsDao>(relaxed = true)
        val playlistRemoteMapDao = mockk<PlaylistRemoteMapDao>(relaxed = true)
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)

        val mappedPlaylistId = "local-1"
        val remotePlaylistId = "10001"

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "playlist": [
                {
                  "id": 10001,
                  "name": "测试歌单",
                  "coverImgUrl": "https://example.com/cover-new.jpg",
                  "trackCount": 3,
                  "updateTime": 1800000000000
                }
              ]
            }
            """
        )
        coEvery {
            playlistRemoteMapDao.getByRemoteSource(
                sourcePlatform = Platform.NETEASE.id,
                sourcePlaylistId = remotePlaylistId,
                ownerUid = session.userId.toString()
            )
        } returns PlaylistRemoteMapEntity(
            playlistId = mappedPlaylistId,
            sourcePlatform = Platform.NETEASE.id,
            sourcePlaylistId = remotePlaylistId,
            ownerUid = session.userId.toString(),
            remoteSignature = "old-signature",
            lastSyncedSongSignature = "netease:10|netease:11",
            lastSyncedAt = 1700000000000
        )
        coEvery { onlineRepo.getPlaylistDetail(Platform.NETEASE, remotePlaylistId) } returns Result.Success(
            listOf(
                track(id = "10"),
                track(id = "11"),
                track(id = "12")
            )
        )
        coEvery { playlistSongsDao.getSongsByPlaylistOnce(mappedPlaylistId) } returns listOf(
            playlistSong(mappedPlaylistId, "10", 0),
            playlistSong(mappedPlaylistId, "11", 1)
        )
        coEvery { playlistsDao.getById(mappedPlaylistId) } returns PlaylistEntity(
            playlistId = mappedPlaylistId,
            name = "旧歌单",
            createdAt = 1234L,
            updatedAt = 1234L
        )
        coEvery {
            enhancedApi.likeList(
                url = "https://music-api.test/likelist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "ids": []
            }
            """
        )
        coEvery { favoritesDao.getSongIdsByPlatform(Platform.NETEASE.id) } returns emptyList()

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            onlineRepo = onlineRepo,
            playlistsDao = playlistsDao,
            playlistSongsDao = playlistSongsDao,
            playlistRemoteMapDao = playlistRemoteMapDao,
            favoritesDao = favoritesDao
        )

        val result = repository.syncLocalLibrary()

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { playlistSongsDao.replacePlaylistSongs(eq(mappedPlaylistId), any()) }

        val playlistEntitySlot = slot<PlaylistEntity>()
        coVerify(exactly = 1) { playlistsDao.insert(capture(playlistEntitySlot)) }
        assertEquals(mappedPlaylistId, playlistEntitySlot.captured.playlistId)
        assertEquals("测试歌单", playlistEntitySlot.captured.name)
        assertEquals("https://example.com/cover-new.jpg", playlistEntitySlot.captured.coverUrl)
        assertEquals(1234L, playlistEntitySlot.captured.createdAt)

        val mappingSlot = slot<PlaylistRemoteMapEntity>()
        coVerify(exactly = 1) { playlistRemoteMapDao.insert(capture(mappingSlot)) }
        assertEquals(mappedPlaylistId, mappingSlot.captured.playlistId)
        assertEquals("ut=1800000000000|tc=3|n=测试歌单|c=https://example.com/cover-new.jpg", mappingSlot.captured.remoteSignature)
        assertEquals("netease:10|netease:11|netease:12", mappingSlot.captured.lastSyncedSongSignature)
    }

    @Test
    fun syncLocalLibraryMergesRemoteAndLocalTracksInSamePlaylistWithoutFork() = runTest {
        val session = defaultSession()
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()
        val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
        val playlistsDao = mockk<PlaylistsDao>(relaxed = true)
        val playlistSongsDao = mockk<PlaylistSongsDao>(relaxed = true)
        val playlistRemoteMapDao = mockk<PlaylistRemoteMapDao>(relaxed = true)
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)

        val mappedPlaylistId = "local-origin"
        val remotePlaylistId = "10002"

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "playlist": [
                {
                  "id": 10002,
                  "name": "我的歌单",
                  "coverImgUrl": "https://example.com/cover.jpg",
                  "trackCount": 3,
                  "updateTime": 1800000000123
                }
              ]
            }
            """
        )
        coEvery {
            playlistRemoteMapDao.getByRemoteSource(
                sourcePlatform = Platform.NETEASE.id,
                sourcePlaylistId = remotePlaylistId,
                ownerUid = session.userId.toString()
            )
        } returns PlaylistRemoteMapEntity(
            playlistId = mappedPlaylistId,
            sourcePlatform = Platform.NETEASE.id,
            sourcePlaylistId = remotePlaylistId,
            ownerUid = session.userId.toString(),
            remoteSignature = "old-signature",
            lastSyncedSongSignature = "netease:1|netease:2",
            lastSyncedAt = 1700000000000
        )
        coEvery { onlineRepo.getPlaylistDetail(Platform.NETEASE, remotePlaylistId) } returns Result.Success(
            listOf(
                track(id = "1"),
                track(id = "2"),
                track(id = "3")
            )
        )
        coEvery { playlistSongsDao.getSongsByPlaylistOnce(mappedPlaylistId) } returns listOf(
            playlistSong(mappedPlaylistId, "1", 0),
            playlistSong(mappedPlaylistId, "99", 1)
        )
        coEvery { playlistsDao.getById(mappedPlaylistId) } returns PlaylistEntity(
            playlistId = mappedPlaylistId,
            name = "本地歌单",
            coverUrl = "https://example.com/old.jpg",
            createdAt = 1234L,
            updatedAt = 1234L
        )
        coEvery {
            enhancedApi.likeList(
                url = "https://music-api.test/likelist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "ids": []
            }
            """
        )
        coEvery { favoritesDao.getSongIdsByPlatform(Platform.NETEASE.id) } returns emptyList()

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            onlineRepo = onlineRepo,
            playlistsDao = playlistsDao,
            playlistSongsDao = playlistSongsDao,
            playlistRemoteMapDao = playlistRemoteMapDao,
            favoritesDao = favoritesDao
        )

        val result = repository.syncLocalLibrary()

        assertTrue(result is Result.Success)

        val playlistEntitySlot = slot<PlaylistEntity>()
        coVerify(exactly = 1) { playlistsDao.insert(capture(playlistEntitySlot)) }
        assertEquals(mappedPlaylistId, playlistEntitySlot.captured.playlistId)
        assertEquals("我的歌单", playlistEntitySlot.captured.name)
        assertEquals(1234L, playlistEntitySlot.captured.createdAt)

        val mergedSongsSlot = slot<List<PlaylistSongEntity>>()
        coVerify(exactly = 1) {
            playlistSongsDao.replacePlaylistSongs(eq(mappedPlaylistId), capture(mergedSongsSlot))
        }
        assertEquals(listOf("1", "2", "3", "99"), mergedSongsSlot.captured.map { it.songId })

        val mappingSlot = slot<PlaylistRemoteMapEntity>()
        coVerify(exactly = 1) { playlistRemoteMapDao.insert(capture(mappingSlot)) }
        assertEquals(mappedPlaylistId, mappingSlot.captured.playlistId)
        assertEquals("10002", mappingSlot.captured.sourcePlaylistId)
    }

    @Test
    fun syncLocalLibraryOnlyAddsDataWithoutDeletingLocalEntries() = runTest {
        val session = defaultSession()
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()
        val playlistsDao = mockk<PlaylistsDao>(relaxed = true)
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "playlist": []
            }
            """
        )
        coEvery {
            enhancedApi.likeList(
                url = "https://music-api.test/likelist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "ids": []
            }
            """
        )
        coEvery { favoritesDao.getSongIdsByPlatform(Platform.NETEASE.id) } returns listOf("1", "2")

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            playlistsDao = playlistsDao,
            favoritesDao = favoritesDao
        )

        val result = repository.syncLocalLibrary()

        assertTrue(result is Result.Success)
        assertEquals(
            NeteaseSyncSummary(syncedPlaylistCount = 0, syncedFavoriteCount = 0),
            (result as Result.Success).data
        )
        coVerify(exactly = 0) { playlistsDao.delete(any()) }
        coVerify(exactly = 0) { favoritesDao.delete(any(), any()) }
    }

    @Test
    fun syncLocalLibraryFetchesOnlyNewLikedSongs() = runTest {
        val session = defaultSession()
        val preferences = mockk<PlayerPreferences>()
        val accountStore = mockk<NeteaseAccountStore>(relaxed = true)
        val enhancedApi = mockk<NeteaseCloudApiEnhancedApi>()
        val tuneHubApi = mockk<TuneHubApi>(relaxed = true)
        val favoritesDao = mockk<FavoritesDao>(relaxed = true)

        every { preferences.neteaseCloudApiBaseUrl } returns flowOf("https://music-api.test")
        every { accountStore.session } returns flowOf(session)
        coEvery {
            enhancedApi.userPlaylist(
                url = "https://music-api.test/user/playlist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "playlist": []
            }
            """
        )
        coEvery {
            enhancedApi.likeList(
                url = "https://music-api.test/likelist",
                uid = session.userId,
                cookie = session.cookie,
                realIp = any(),
                timestamp = any()
            )
        } returns json(
            """
            {
              "code": 200,
              "ids": [100, 101, 102]
            }
            """
        )
        coEvery { favoritesDao.getSongIdsByPlatform(Platform.NETEASE.id) } returns listOf("101")
        coEvery { tuneHubApi.getNeteaseSongDetail(ids = any()) } returns json(
            """
            {
              "songs": [
                {
                  "id": 100,
                  "name": "歌A",
                  "dt": 180000,
                  "ar": [{"name": "歌手A"}],
                  "al": {"id": 1, "name": "专辑A", "picUrl": "https://example.com/a.jpg"}
                },
                {
                  "id": 102,
                  "name": "歌C",
                  "dt": 200000,
                  "ar": [{"name": "歌手C"}],
                  "al": {"id": 3, "name": "专辑C", "picUrl": "https://example.com/c.jpg"}
                }
              ]
            }
            """
        )

        val repository = createRepository(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            tuneHubApi = tuneHubApi,
            favoritesDao = favoritesDao
        )

        val result = repository.syncLocalLibrary()

        assertTrue(result is Result.Success)
        assertEquals(2, (result as Result.Success).data.syncedFavoriteCount)
        coVerify(exactly = 1) {
            tuneHubApi.getNeteaseSongDetail(
                ids = match { ids ->
                    ids.contains("100") && ids.contains("102") && !ids.contains("101")
                }
            )
        }
        coVerify(exactly = 2) { favoritesDao.insert(any()) }
    }

    private fun createRepository(
        preferences: PlayerPreferences,
        accountStore: NeteaseAccountStore,
        enhancedApi: NeteaseCloudApiEnhancedApi,
        tuneHubApi: TuneHubApi = mockk(relaxed = true),
        onlineRepo: OnlineMusicRepository = mockk(relaxed = true),
        playlistsDao: PlaylistsDao = mockk(relaxed = true),
        playlistSongsDao: PlaylistSongsDao = mockk(relaxed = true),
        playlistRemoteMapDao: PlaylistRemoteMapDao = mockk(relaxed = true),
        favoritesDao: FavoritesDao = mockk(relaxed = true)
    ): NeteaseAccountRepositoryImpl {
        return NeteaseAccountRepositoryImpl(
            preferences = preferences,
            accountStore = accountStore,
            enhancedApi = enhancedApi,
            tuneHubApi = tuneHubApi,
            onlineRepo = onlineRepo,
            playlistsDao = playlistsDao,
            playlistSongsDao = playlistSongsDao,
            playlistRemoteMapDao = playlistRemoteMapDao,
            favoritesDao = favoritesDao
        )
    }

    private fun defaultSession(): NeteaseAccountSession = NeteaseAccountSession(
        userId = 9527L,
        nickname = "测试用户",
        cookie = "MUSIC_U=test_cookie"
    )

    private fun track(id: String): Track = Track(
        id = id,
        platform = Platform.NETEASE,
        title = "song-$id",
        artist = "artist-$id"
    )

    private fun playlistSong(playlistId: String, songId: String, order: Int): PlaylistSongEntity =
        PlaylistSongEntity(
            playlistId = playlistId,
            songId = songId,
            platform = Platform.NETEASE.id,
            orderInPlaylist = order,
            title = "song-$songId",
            artist = "artist-$songId"
        )

    private fun json(raw: String) = Json.parseToJsonElement(raw.trimIndent())
}
