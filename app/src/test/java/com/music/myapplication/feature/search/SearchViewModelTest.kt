package com.music.myapplication.feature.search

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @Test
    fun userSelectionIsNotOverriddenByDelayedPreferenceRestore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
            val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow {
                delay(100)
                emit(Platform.KUWO.id)
            }
            coEvery { preferences.setPlatform(any()) } returns Unit

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences)

            viewModel.onPlatformChange(Platform.QQ)
            advanceUntilIdle()

            assertEquals(Platform.QQ, viewModel.state.value.platform)
            coVerify(exactly = 1) { preferences.setPlatform(Platform.QQ.id) }
            coVerify(exactly = 0) { onlineRepo.search(any(), any(), any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun delayedPreferenceRestoreDoesNotTriggerSearchOnWrongPlatform() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow {
                delay(100)
                emit(Platform.KUWO.id)
            }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
            coEvery { onlineRepo.search(any(), any(), any(), any()) } answers {
                val platform = firstArg<Platform>()
                val keyword = secondArg<String>()
                val page = thirdArg<Int>()
                Result.Success(listOf(testTrack(platform, "$keyword-$page")))
            }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences)

            viewModel.onQueryChange("夜曲")
            viewModel.onPlatformChange(Platform.QQ)
            advanceUntilIdle()

            assertEquals(Platform.QQ, viewModel.state.value.platform)
            assertTrue(viewModel.state.value.tracks.isNotEmpty())
            assertEquals(Platform.QQ, viewModel.state.value.tracks.first().platform)
            coVerify(atLeast = 1) { onlineRepo.search(Platform.QQ, "夜曲", 1, any()) }
            coVerify(exactly = 0) { onlineRepo.search(Platform.KUWO, any(), any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun testTrack(platform: Platform, id: String) = Track(
        id = id,
        platform = platform,
        title = "测试歌曲",
        artist = "测试歌手"
    )
}
