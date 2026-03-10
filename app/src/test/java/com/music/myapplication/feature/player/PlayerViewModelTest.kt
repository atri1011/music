package com.music.myapplication.feature.player

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.state.CommentsStateHolder
import com.music.myapplication.feature.player.state.LyricsStateHolder
import com.music.myapplication.feature.player.state.PlaybackControlStateHolder
import com.music.myapplication.feature.player.state.SleepTimerStateHolder
import com.music.myapplication.feature.player.state.TrackInfoStateHolder
import com.music.myapplication.media.player.QueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @Test
    fun addTrackToPlaylistReturnsAlreadyExistsWhenTrackIsPresent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val localRepo = mockk<LocalLibraryRepository>()
            val track = Track(
                id = "track-1",
                platform = Platform.QQ,
                title = "晴天",
                artist = "周杰伦"
            )
            val playlist = Playlist(id = "playlist-1", name = "常听")

            every { localRepo.getPlaylists() } returns flowOf(emptyList())
            every { localRepo.getPlaylistSongs("playlist-1") } returns flowOf(listOf(track))

            val viewModel = createViewModel(localRepo)
            val result = viewModel.addTrackToPlaylist(playlist, track)

            assertFalse(result.added)
            assertEquals("歌曲已在「常听」里了", result.message)
            coVerify(exactly = 0) { localRepo.addToPlaylist(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun createPlaylistAndAddTrackAddsTrackToNewPlaylist() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val localRepo = mockk<LocalLibraryRepository>()
            val track = Track(
                id = "track-2",
                platform = Platform.NETEASE,
                title = "夜曲",
                artist = "周杰伦"
            )
            val playlist = Playlist(id = "playlist-2", name = "深夜单曲循环")

            every { localRepo.getPlaylists() } returns flowOf(emptyList())
            coEvery { localRepo.createPlaylist("深夜单曲循环") } returns playlist
            coEvery { localRepo.addToPlaylist("playlist-2", track) } returns Unit

            val viewModel = createViewModel(localRepo)
            val result = viewModel.createPlaylistAndAddTrack("  深夜单曲循环  ", track)

            assertTrue(result.added)
            assertTrue(result.created)
            assertEquals("已添加到新歌单「深夜单曲循环」", result.message)
            coVerify(exactly = 1) { localRepo.createPlaylist("深夜单曲循环") }
            coVerify(exactly = 1) { localRepo.addToPlaylist("playlist-2", track) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(localRepo: LocalLibraryRepository): PlayerViewModel {
        val playback = mockk<PlaybackControlStateHolder>(relaxed = true)
        val lyrics = mockk<LyricsStateHolder>(relaxed = true)
        val comments = mockk<CommentsStateHolder>(relaxed = true)
        val trackInfo = mockk<TrackInfoStateHolder>(relaxed = true)
        val sleepTimer = mockk<SleepTimerStateHolder>(relaxed = true)

        return PlayerViewModel(
            playback = playback,
            lyrics = lyrics,
            comments = comments,
            trackInfo = trackInfo,
            sleepTimer = sleepTimer,
            queueManager = QueueManager(),
            localRepo = localRepo
        )
    }
}
