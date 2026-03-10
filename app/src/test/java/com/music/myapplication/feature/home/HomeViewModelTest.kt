package com.music.myapplication.feature.home

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.NetworkMonitor
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.GuessYouLikeResult
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Test
    fun refreshGuessYouLike_keepsPreviousTracksWhenNewResultIsEmpty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val recommendationRepo = mockk<RecommendationRepository>()
            val preferences = mockk<PlayerPreferences>()
            val networkMonitor = mockk<NetworkMonitor>()
            val firstTracks = listOf(
                testTrack("guess-1"),
                testTrack("guess-2")
            )

            every { preferences.wifiOnly } returns flowOf(false)
            every { networkMonitor.isUnmeteredConnection() } returns true
            coEvery { onlineRepo.getToplists(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getToplistDetailFast(any(), any()) } returns Result.Success(emptyList())
            coEvery { recommendationRepo.getDailyRecommendedTracks(any()) } returns emptyList()
            coEvery { recommendationRepo.getFmTrack() } returns null
            coEvery { recommendationRepo.getRecommendedPlaylists() } returns emptyList()
            coEvery { recommendationRepo.getGuessYouLikeTracks(any(), any()) } returnsMany listOf(
                GuessYouLikeResult(label = "周杰伦", tracks = firstTracks),
                GuessYouLikeResult(label = "热门推荐", tracks = emptyList())
            )

            val viewModel = HomeViewModel(onlineRepo, recommendationRepo, preferences, networkMonitor)
            advanceUntilIdle()

            assertEquals("周杰伦", viewModel.state.value.guessYouLikeLabel)
            assertEquals(listOf("guess-1", "guess-2"), viewModel.state.value.guessYouLikeTracks.map { it.id })

            viewModel.refreshGuessYouLike()
            advanceUntilIdle()

            assertEquals("周杰伦", viewModel.state.value.guessYouLikeLabel)
            assertEquals(listOf("guess-1", "guess-2"), viewModel.state.value.guessYouLikeTracks.map { it.id })
            assertFalse(viewModel.state.value.isGuessYouLikeLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun wifiOnly_onMeteredNetwork_skipsToplistPreviewPreload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val recommendationRepo = mockk<RecommendationRepository>()
            val preferences = mockk<PlayerPreferences>()
            val networkMonitor = mockk<NetworkMonitor>()

            every { preferences.wifiOnly } returns flowOf(true)
            every { networkMonitor.isUnmeteredConnection() } returns false
            coEvery { onlineRepo.getToplists(Platform.NETEASE) } returns Result.Success(
                listOf(
                    com.music.myapplication.domain.repository.ToplistInfo(
                        id = "top-1",
                        name = "热歌榜"
                    )
                )
            )
            coEvery { recommendationRepo.getDailyRecommendedTracks(any()) } returns emptyList()
            coEvery { recommendationRepo.getFmTrack() } returns null
            coEvery { recommendationRepo.getRecommendedPlaylists() } returns emptyList()
            coEvery { recommendationRepo.getGuessYouLikeTracks(any(), any()) } returns GuessYouLikeResult(
                label = "",
                tracks = emptyList()
            )

            val viewModel = HomeViewModel(onlineRepo, recommendationRepo, preferences, networkMonitor)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.toplistPreviews.isEmpty())
            io.mockk.coVerify(exactly = 0) { onlineRepo.getToplistDetailFast(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun testTrack(id: String) = Track(
        id = id,
        platform = Platform.NETEASE,
        title = "测试歌曲-$id",
        artist = "测试歌手"
    )
}
