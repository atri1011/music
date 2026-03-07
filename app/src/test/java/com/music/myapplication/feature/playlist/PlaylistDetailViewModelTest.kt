package com.music.myapplication.feature.playlist

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
}
