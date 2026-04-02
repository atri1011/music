package com.music.myapplication.feature.search

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.datastore.SearchHistoryStore
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchSuggestion
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.SuggestionType
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @Test
    fun switchingFromSongToAlbumIgnoresStaleSongResponse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.NETEASE.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
            coEvery { onlineRepo.search(Platform.NETEASE, "夜曲", 1, any()) } coAnswers {
                try {
                    delay(100)
                } catch (_: CancellationException) {
                }
                Result.Success(listOf(testTrack(Platform.NETEASE, "song-1")))
            }
            coEvery { onlineRepo.searchAlbums(Platform.NETEASE, "夜曲", 1, any()) } returns Result.Success(
                listOf(
                    SearchResultItem(
                        id = "album-1",
                        title = "叶惠美",
                        subtitle = "周杰伦",
                        platform = Platform.NETEASE,
                        type = SearchType.ALBUM,
                        trackCount = 11
                    )
                )
            )
            coEvery { onlineRepo.searchArtists(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchPlaylists(any(), any(), any(), any()) } returns Result.Success(emptyList())

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            viewModel.onQueryChange("夜曲")
            viewModel.submitSearch()
            advanceTimeBy(1)
            viewModel.onSearchTypeChange(SearchType.ALBUM)
            advanceUntilIdle()

            assertEquals(SearchType.ALBUM, viewModel.state.value.searchType)
            assertTrue(viewModel.state.value.tracks.isEmpty())
            assertEquals(1, viewModel.state.value.genericResults.size)
            assertEquals("album-1", viewModel.state.value.genericResults.first().id)
            coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "夜曲", 1, any()) }
            coVerify(exactly = 1) { onlineRepo.searchAlbums(Platform.NETEASE, "夜曲", 1, any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

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

    @Test
    fun submitSearchAndQuickPickKeywordsShareConsistentSubmitBehavior() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.NETEASE.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchArtists(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchAlbums(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchPlaylists(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            viewModel.onQueryChange("周杰伦")
            viewModel.submitSearch()
            advanceUntilIdle()

            viewModel.onHistoryClick("夜曲")
            advanceUntilIdle()

            viewModel.onHotKeywordClick("稻香")
            advanceUntilIdle()

            viewModel.onSuggestionClick(SearchSuggestion("晴天", SuggestionType.SONG))
            advanceUntilIdle()

            assertEquals("晴天", viewModel.state.value.query)
            assertFalse(viewModel.state.value.showSuggestions)
            assertTrue(viewModel.state.value.suggestions.isEmpty())

            coVerify(exactly = 1) { historyStore.record("周杰伦") }
            coVerify(exactly = 1) { historyStore.record("夜曲") }
            coVerify(exactly = 1) { historyStore.record("稻香") }
            coVerify(exactly = 1) { historyStore.record("晴天") }

            coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "周杰伦", 1, any()) }
            coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "夜曲", 1, any()) }
            coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "稻香", 1, any()) }
            coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "晴天", 1, any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dismissSuggestionsDoesNotReopenWhenLateSuggestionResponseArrives() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.NETEASE.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
            coEvery { onlineRepo.getSearchSuggestions(Platform.NETEASE, "夜曲") } coAnswers {
                delay(100)
                Result.Success(listOf(SearchSuggestion("夜曲", SuggestionType.KEYWORD)))
            }

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            viewModel.onQueryChange("夜曲")
            advanceTimeBy(151)
            viewModel.dismissSuggestions()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showSuggestions)
            assertFalse(viewModel.state.value.isSuggestionLoading)
            assertEquals(listOf(SearchSuggestion("夜曲", SuggestionType.KEYWORD)), viewModel.state.value.suggestions)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submittingSameSongQueryTwiceUsesCachedResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.NETEASE.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
            coEvery { onlineRepo.search(Platform.NETEASE, "夜曲", 1, any()) } returns Result.Success(
                listOf(testTrack(Platform.NETEASE, "song-1"))
            )

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            viewModel.onQueryChange("夜曲")
            viewModel.submitSearch()
            advanceUntilIdle()

            viewModel.onHistoryClick("夜曲")
            advanceUntilIdle()

            assertEquals(listOf("song-1"), viewModel.state.value.tracks.map(Track::id))
            coVerify(exactly = 1) { onlineRepo.search(Platform.NETEASE, "夜曲", 1, any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submittingSameAlbumQueryTwiceUsesCachedGenericResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.NETEASE.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.getSearchSuggestions(any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchArtists(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchPlaylists(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.searchAlbums(Platform.NETEASE, "叶惠美", 1, any()) } returns Result.Success(
                listOf(
                    SearchResultItem(
                        id = "album-1",
                        title = "叶惠美",
                        subtitle = "周杰伦",
                        platform = Platform.NETEASE,
                        type = SearchType.ALBUM,
                        trackCount = 11
                    )
                )
            )

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            viewModel.onSearchTypeChange(SearchType.ALBUM)
            advanceUntilIdle()

            viewModel.onQueryChange("叶惠美")
            viewModel.submitSearch()
            advanceUntilIdle()

            viewModel.onHistoryClick("叶惠美")
            advanceUntilIdle()

            assertEquals(listOf("album-1"), viewModel.state.value.genericResults.map(SearchResultItem::id))
            coVerify(exactly = 1) { onlineRepo.searchAlbums(Platform.NETEASE, "叶惠美", 1, any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatingSuggestionQueryUsesCachedSuggestions() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val preferences = mockk<PlayerPreferences>()
            every { preferences.platform } returns flow { emit(Platform.NETEASE.id) }
            coEvery { preferences.setPlatform(any()) } returns Unit
            coEvery { onlineRepo.getHotSearchKeywords(any()) } returns Result.Success(emptyList())
            coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())
            coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
            coEvery { onlineRepo.getSearchSuggestions(Platform.NETEASE, "夜曲") } returns Result.Success(
                listOf(SearchSuggestion("夜曲", SuggestionType.KEYWORD))
            )

            val historyStore = mockk<SearchHistoryStore>(relaxed = true)
            every { historyStore.history } returns flow { emit(emptyList<String>()) }

            val viewModel = SearchViewModel(onlineRepo, localRepo, preferences, historyStore)
            advanceUntilIdle()

            viewModel.onQueryChange("夜曲")
            advanceUntilIdle()

            viewModel.onQueryChange("")
            advanceUntilIdle()

            viewModel.onQueryChange("夜曲")
            advanceUntilIdle()

            assertEquals(listOf(SearchSuggestion("夜曲", SuggestionType.KEYWORD)), viewModel.state.value.suggestions)
            coVerify(exactly = 1) { onlineRepo.getSearchSuggestions(Platform.NETEASE, "夜曲") }
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
