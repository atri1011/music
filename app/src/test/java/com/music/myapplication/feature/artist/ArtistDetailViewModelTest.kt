package com.music.myapplication.feature.artist

import androidx.lifecycle.SavedStateHandle
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.ArtistDetail
import com.music.myapplication.domain.model.ArtistRef
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    @Test
    fun artistSearchResult_usesArtistIdDirectlyWithoutResolvingSong() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val detail = ArtistDetail(
                id = "6452",
                name = "周杰伦",
                platform = Platform.NETEASE,
                avatarUrl = "https://example.com/jay.jpg",
                hotSongs = listOf(
                    Track(id = "185811", platform = Platform.NETEASE, title = "晴天", artist = "周杰伦")
                )
            )
            coEvery { onlineRepo.getArtistDetail("6452", Platform.NETEASE) } returns Result.Success(detail)

            val viewModel = ArtistDetailViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(
                        "artistId" to "6452",
                        "platform" to Platform.NETEASE.id,
                        "artistName" to "周杰伦"
                    )
                ),
                onlineRepo = onlineRepo
            )

            advanceUntilIdle()

            assertEquals("6452", viewModel.state.value.artistId)
            assertEquals("周杰伦", viewModel.state.value.artistName)
            assertEquals("https://example.com/jay.jpg", viewModel.state.value.avatarUrl)
            assertEquals(1, viewModel.state.value.hotSongs.size)
            assertEquals(false, viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.error)
            coVerify(exactly = 1) { onlineRepo.getArtistDetail("6452", Platform.NETEASE) }
            coVerify(exactly = 0) { onlineRepo.resolveArtistRef(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun songArtistEntry_fallsBackToResolveArtistRefWhenDirectLoadFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val detail = ArtistDetail(
                id = "0025NhlN2yWrP4",
                name = "周杰伦",
                platform = Platform.QQ,
                avatarUrl = "https://example.com/jay-qq.jpg"
            )
            coEvery { onlineRepo.getArtistDetail("0039MnYb0qxYhV", Platform.QQ) } returns
                Result.Error(com.music.myapplication.core.common.AppError.Api(message = "artist not found"))
            coEvery { onlineRepo.resolveArtistRef(any()) } returns
                Result.Success(ArtistRef("0025NhlN2yWrP4", "周杰伦", Platform.QQ))
            coEvery { onlineRepo.getArtistDetail("0025NhlN2yWrP4", Platform.QQ) } returns
                Result.Success(detail)

            val viewModel = ArtistDetailViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(
                        "artistId" to "0039MnYb0qxYhV",
                        "platform" to Platform.QQ.id,
                        "artistName" to "周杰伦"
                    )
                ),
                onlineRepo = onlineRepo
            )

            advanceUntilIdle()

            assertEquals("0025NhlN2yWrP4", viewModel.state.value.artistId)
            assertEquals("周杰伦", viewModel.state.value.artistName)
            assertEquals("https://example.com/jay-qq.jpg", viewModel.state.value.avatarUrl)
            assertEquals(false, viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.error)
            coVerify(exactly = 1) { onlineRepo.getArtistDetail("0039MnYb0qxYhV", Platform.QQ) }
            coVerify(exactly = 1) { onlineRepo.resolveArtistRef(any()) }
            coVerify(exactly = 1) { onlineRepo.getArtistDetail("0025NhlN2yWrP4", Platform.QQ) }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
