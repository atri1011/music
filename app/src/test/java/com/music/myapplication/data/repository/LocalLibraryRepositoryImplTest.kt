package com.music.myapplication.data.repository

import com.music.myapplication.core.cache.CacheManager
import com.music.myapplication.core.database.dao.FavoritesDao
import com.music.myapplication.core.database.dao.LocalTracksDao
import com.music.myapplication.core.database.dao.LyricsCacheDao
import com.music.myapplication.core.database.dao.PlaylistSongsDao
import com.music.myapplication.core.database.dao.PlaylistsDao
import com.music.myapplication.core.database.dao.RecentPlaysDao
import com.music.myapplication.core.database.entity.FavoriteEntity
import com.music.myapplication.core.database.entity.LocalTrackEntity
import com.music.myapplication.core.database.entity.PlaylistSongEntity
import com.music.myapplication.core.database.entity.RecentPlayEntity
import com.music.myapplication.core.local.LocalMusicScanner
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.Platform
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryRepositoryImplTest {

    private val context = mockk<android.content.Context>(relaxed = true)
    private val favoritesDao = mockk<FavoritesDao>(relaxed = true)
    private val recentPlaysDao = mockk<RecentPlaysDao>(relaxed = true)
    private val playlistsDao = mockk<PlaylistsDao>(relaxed = true)
    private val playlistSongsDao = mockk<PlaylistSongsDao>(relaxed = true)
    private val lyricsCacheDao = mockk<LyricsCacheDao>(relaxed = true)
    private val cacheManager = mockk<CacheManager>(relaxed = true)
    private val localTracksDao = mockk<LocalTracksDao>(relaxed = true)
    private val localMusicScanner = mockk<LocalMusicScanner>(relaxed = true)
    private val tuneHubApi = mockk<TuneHubApi>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private val repository = LocalLibraryRepositoryImpl(
        context = context,
        favoritesDao = favoritesDao,
        recentPlaysDao = recentPlaysDao,
        playlistsDao = playlistsDao,
        playlistSongsDao = playlistSongsDao,
        lyricsCacheDao = lyricsCacheDao,
        cacheManager = cacheManager,
        localTracksDao = localTracksDao,
        localMusicScanner = localMusicScanner,
        tuneHubApi = tuneHubApi
    )

    @Test
    fun `local playlist tracks are rehydrated with media store uri`() = runTest {
        every { playlistSongsDao.getSongsByPlaylist("local-playlist") } returns flowOf(
            listOf(
                PlaylistSongEntity(
                    playlistId = "local-playlist",
                    songId = "42",
                    platform = Platform.LOCAL.id,
                    orderInPlaylist = 0,
                    title = "本地歌",
                    artist = "歌手",
                    durationMs = 0L
                )
            )
        )
        coEvery { localTracksDao.getByIds(listOf(42L)) } returns listOf(
            localTrackEntity(mediaStoreId = 42L, filePath = "content://media/external/audio/media/42")
        )

        val tracks = repository.getPlaylistSongs("local-playlist").first()

        assertEquals(1, tracks.size)
        assertEquals(Platform.LOCAL, tracks.first().platform)
        assertEquals("content://media/external/audio/media/42", tracks.first().playableUrl)
        assertEquals("专辑", tracks.first().album)
        assertTrue(tracks.first().durationMs > 0L)
    }

    @Test
    fun `random recent local track is rehydrated before returning`() = runTest {
        coEvery { recentPlaysDao.getRandomTrack() } returns RecentPlayEntity(
            songId = "7",
            platform = Platform.LOCAL.id,
            title = "旧歌",
            artist = "老歌手"
        )
        coEvery { localTracksDao.getByIds(listOf(7L)) } returns listOf(
            localTrackEntity(mediaStoreId = 7L, filePath = "content://media/external/audio/media/7")
        )

        val track = repository.getRandomRecentTrack()

        requireNotNull(track)
        assertEquals(Platform.LOCAL, track.platform)
        assertEquals("content://media/external/audio/media/7", track.playableUrl)
        assertEquals("专辑", track.album)
    }

    @Test
    fun `cache lyrics triggers cache limit enforcement`() = runTest {
        repository.cacheLyrics(Platform.NETEASE.id, "song-1", "这是一段歌词")

        coVerify(exactly = 1) { lyricsCacheDao.insert(any()) }
        coVerify(exactly = 1) { cacheManager.enforceLimit() }
    }

    @Test
    fun `favorites flow repairs netease missing metadata`() = runTest {
        every { favoritesDao.getAll() } returns flowOf(
            listOf(
                FavoriteEntity(
                    songId = "101",
                    platform = Platform.NETEASE.id,
                    title = "歌B",
                    artist = "未知歌手",
                    coverUrl = ""
                )
            )
        )
        coEvery { tuneHubApi.getNeteaseSongDetail(ids = any()) } returns json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": 101,
                  "name": "歌B",
                  "dt": 190000,
                  "artists": [{"name": "歌手B"}],
                  "album": {"id": 2, "name": "专辑B", "picUrl": "https://example.com/b.jpg"}
                }
              ]
            }
            """.trimIndent()
        )

        val tracks = repository.getFavorites().first()

        assertEquals(1, tracks.size)
        assertEquals("歌手B", tracks.first().artist)
        assertEquals("https://example.com/b.jpg", tracks.first().coverUrl)
        coVerify(exactly = 1) {
            favoritesDao.insert(
                match { entity ->
                    entity.songId == "101" &&
                        entity.artist == "歌手B" &&
                        entity.coverUrl == "https://example.com/b.jpg"
                }
            )
        }
    }

    private fun localTrackEntity(
        mediaStoreId: Long,
        filePath: String
    ) = LocalTrackEntity(
        mediaStoreId = mediaStoreId,
        title = "本地歌",
        artist = "歌手",
        album = "专辑",
        durationMs = 180_000L,
        filePath = filePath,
        fileSizeBytes = 2048L,
        mimeType = "audio/mpeg",
        addedAt = 1_700_000_000_000L
    )
}
