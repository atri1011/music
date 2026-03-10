package com.music.myapplication.feature.playlist

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    @Test
    fun qqToplistLoadsTracksBeforeArtworkHydrationFinishes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val baseTracks = listOf(
                Track(
                    id = "qq-track-1",
                    platform = Platform.QQ,
                    title = "起风了",
                    artist = "买辣椒也用券"
                )
            )
            val hydratedTracks = listOf(
                baseTracks.first().copy(coverUrl = "https://example.com/qq-cover.jpg")
            )

            coEvery { onlineRepo.getToplistDetailFast(Platform.QQ, "26") } returns Result.Success(baseTracks)
            coEvery { onlineRepo.enrichToplistTracks(Platform.QQ, "26", baseTracks) } coAnswers {
                delay(100)
                hydratedTracks
            }
            coEvery { localRepo.applyFavoriteState(baseTracks) } returns baseTracks
            coEvery { localRepo.applyFavoriteState(hydratedTracks) } returns hydratedTracks

            val viewModel = PlaylistDetailViewModel(onlineRepo, localRepo)

            viewModel.loadPlaylist(id = "26", platform = Platform.QQ.id, title = "热歌榜")
            runCurrent()

            assertEquals("起风了", viewModel.state.value.tracks.firstOrNull()?.title)
            assertTrue(viewModel.state.value.tracks.firstOrNull()?.coverUrl.isNullOrBlank())
            assertEquals(false, viewModel.state.value.isLoading)

            advanceTimeBy(100)
            advanceUntilIdle()

            assertEquals(
                "https://example.com/qq-cover.jpg",
                viewModel.state.value.tracks.firstOrNull()?.coverUrl
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun localPlaylistCommitPersistsEditedOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val trackA = Track(id = "1", platform = Platform.LOCAL, title = "A", artist = "AA")
            val trackB = Track(id = "2", platform = Platform.LOCAL, title = "B", artist = "BB")
            everyLocalPlaylist(localRepo, listOf(trackA, trackB))
            coJustRun { localRepo.replacePlaylistSongs("local-playlist", any()) }

            val viewModel = PlaylistDetailViewModel(onlineRepo, localRepo)

            viewModel.loadPlaylist(
                id = "local-playlist",
                platform = Platform.LOCAL.id,
                title = "本地歌单",
                source = "local"
            )
            advanceUntilIdle()

            viewModel.enterEditMode()
            viewModel.moveEditingTrack(0, 1)
            viewModel.commitPlaylistEdits()
            advanceUntilIdle()

            coVerify {
                localRepo.replacePlaylistSongs(
                    "local-playlist",
                    listOf(trackB, trackA)
                )
            }
            assertEquals(listOf(trackB, trackA), viewModel.state.value.tracks)
            assertEquals("/data/user/0/com.music.myapplication/files/playlist_covers/custom.jpg", viewModel.state.value.coverUrl)
            assertEquals(false, viewModel.state.value.isEditMode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun localPlaylistCommitFailureKeepsEditModeAndShowsInlineMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val trackA = Track(id = "1", platform = Platform.LOCAL, title = "A", artist = "AA")
            val trackB = Track(id = "2", platform = Platform.LOCAL, title = "B", artist = "BB")
            everyLocalPlaylist(localRepo, listOf(trackA, trackB))
            coEvery {
                localRepo.replacePlaylistSongs("broken-playlist", any())
            } throws IllegalStateException("保存失败")

            val viewModel = PlaylistDetailViewModel(onlineRepo, localRepo)

            viewModel.loadPlaylist(
                id = "broken-playlist",
                platform = Platform.LOCAL.id,
                title = "本地歌单",
                source = "local"
            )
            advanceUntilIdle()

            viewModel.enterEditMode()
            viewModel.removeEditingTrack(trackA)
            viewModel.commitPlaylistEdits()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isEditMode)
            assertEquals("保存失败", viewModel.state.value.editMessage)
            assertEquals(null, viewModel.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun everyLocalPlaylist(localRepo: LocalLibraryRepository, tracks: List<Track>) {
        coEvery { localRepo.replacePlaylistSongs(any(), any()) } returns Unit
        coEvery { localRepo.getPlaylistById(any()) } answers {
            Playlist(
                id = firstArg(),
                name = "本地歌单",
                coverUrl = "/data/user/0/com.music.myapplication/files/playlist_covers/custom.jpg"
            )
        }
        io.mockk.every { localRepo.getPlaylistSongs(any()) } returns flowOf(tracks)
    }
}
