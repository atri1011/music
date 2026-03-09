package com.music.myapplication.feature.search

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.datastore.SearchHistoryStore
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
    fun switchBackToPlatformRestoresCachedHotKeywordsWhenReloadFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.QQ.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getHotSearchKeywords(Platform.QQ) } returnsMany listOf(
                Result.Success(listOf("周杰伦", "林俊杰")),
                Result.Error(AppError.Network(message = "boom"))
            )
            coEvery { onlineRepo.getHotSearchKeywords(Platform.KUWO) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getHotSearchKeywords(Platform.NETEASE) } returns Result.Success(emptyList())

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            assertEquals(listOf("周杰伦", "林俊杰"), viewModel.state.value.hotKeywords)

            viewModel.onPlatformChange(Platform.KUWO)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.hotKeywords.isEmpty())

            viewModel.onPlatformChange(Platform.QQ)
            advanceUntilIdle()

            assertEquals(Platform.QQ, viewModel.state.value.platform)
            assertEquals(listOf("周杰伦", "林俊杰"), viewModel.state.value.hotKeywords)
            assertTrue(!viewModel.state.value.isHotLoading)
            coVerify(exactly = 2) { onlineRepo.getHotSearchKeywords(Platform.QQ) }
        } finally {
            Dispatchers.resetMain()
        }
    }

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
            coEvery { onlineRepo.getHotSearchKeywords(Platform.QQ) } returns Result.Success(listOf("周杰伦"))
            coEvery { onlineRepo.getHotSearchKeywords(Platform.KUWO) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getHotSearchKeywords(Platform.NETEASE) } returns Result.Success(emptyList())

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)

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
    fun delayedPreferenceRestoreDoesNotLeaveHotSearchLoadingOnCurrentPlatform() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow {
                delay(100)
                emit(Platform.KUWO.id)
            }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(Platform.QQ) } returns Result.Success(listOf("周杰伦"))
            coEvery { onlineRepo.getHotSearchKeywords(Platform.KUWO) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getHotSearchKeywords(Platform.NETEASE) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)

            viewModel.onPlatformChange(Platform.QQ)
            advanceUntilIdle()

            assertEquals(Platform.QQ, viewModel.state.value.platform)
            assertEquals(listOf("周杰伦"), viewModel.state.value.hotKeywords)
            assertTrue(!viewModel.state.value.isHotLoading)
            coVerify(exactly = 1) { onlineRepo.getHotSearchKeywords(Platform.QQ) }
            coVerify(exactly = 1) { onlineRepo.getHotSearchKeywords(Platform.KUWO) }
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
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } answers {
                val platform = firstArg<Platform>()
                val keyword = secondArg<String>()
                val page = thirdArg<Int>()
                Result.Success(listOf(testTrack(platform, "$keyword-$page")))
            }

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)

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
