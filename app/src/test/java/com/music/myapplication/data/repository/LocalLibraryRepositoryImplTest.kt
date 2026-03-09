package com.music.myapplication.data.repository

import com.music.myapplication.core.database.dao.FavoritesDao
import com.music.myapplication.core.database.dao.LocalTracksDao
import com.music.myapplication.core.database.dao.LyricsCacheDao
import com.music.myapplication.core.database.dao.PlaylistSongsDao
import com.music.myapplication.core.database.dao.PlaylistsDao
import com.music.myapplication.core.database.dao.RecentPlaysDao
import com.music.myapplication.core.database.entity.LocalTrackEntity
import com.music.myapplication.core.database.entity.PlaylistSongEntity
import com.music.myapplication.core.database.entity.RecentPlayEntity
import com.music.myapplication.core.local.LocalMusicScanner
import com.music.myapplication.domain.model.Platform
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryRepositoryImplTest {

    private val favoritesDao = mockk<FavoritesDao>(relaxed = true)
    private val recentPlaysDao = mockk<RecentPlaysDao>(relaxed = true)
    private val playlistsDao = mockk<PlaylistsDao>(relaxed = true)
    private val playlistSongsDao = mockk<PlaylistSongsDao>(relaxed = true)
    private val lyricsCacheDao = mockk<LyricsCacheDao>(relaxed = true)
    private val localTracksDao = mockk<LocalTracksDao>(relaxed = true)
    private val localMusicScanner = mockk<LocalMusicScanner>(relaxed = true)

    private val repository = LocalLibraryRepositoryImpl(
        favoritesDao = favoritesDao,
        recentPlaysDao = recentPlaysDao,
        playlistsDao = playlistsDao,
        playlistSongsDao = playlistSongsDao,
        lyricsCacheDao = lyricsCacheDao,
        localTracksDao = localTracksDao,
        localMusicScanner = localMusicScanner
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
