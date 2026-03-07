package com.music.myapplication.feature.library

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class LibraryViewModelTest {

    @Test
    fun importPlaylistCreatesLocalPlaylistWithRemoteTracks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val playlistsFlow = MutableStateFlow(emptyList<Playlist>())
            val importedTracks = listOf(
                Track(
                    id = "1974443814",
                    platform = Platform.NETEASE,
                    title = "测试歌曲",
                    artist = "测试歌手",
                    coverUrl = "https://example.com/cover.jpg"
                )
            )

            every { localRepo.getFavorites() } returns flowOf(emptyList())
            every { localRepo.getTopPlayedTracks(any()) } returns flowOf(emptyList())
            every { localRepo.getPlaylists() } returns playlistsFlow
            every { localRepo.getTotalPlayCount() } returns flowOf(0)
            every { localRepo.getTotalListenDurationMs() } returns flowOf(0L)
            coEvery { onlineRepo.getPlaylistDetail(Platform.NETEASE, "19723756") } returns Result.Success(importedTracks)
            coEvery { localRepo.createPlaylist("夜曲合集") } returns Playlist(id = "local-1", name = "夜曲合集")
            coEvery { localRepo.addAllToPlaylist("local-1", importedTracks) } returns Unit

            val viewModel = LibraryViewModel(localRepo, onlineRepo)
            viewModel.showImportDialog(true)
            viewModel.importPlaylist(
                platform = Platform.NETEASE,
                rawInput = "https://music.163.com/#/playlist?id=19723756",
                customName = "夜曲合集"
            )

            advanceUntilIdle()

            coVerify(exactly = 1) { onlineRepo.getPlaylistDetail(Platform.NETEASE, "19723756") }
            coVerify(exactly = 1) { localRepo.createPlaylist("夜曲合集") }
            coVerify(exactly = 1) { localRepo.addAllToPlaylist("local-1", importedTracks) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidImportInputShowsErrorWithoutCallingRemoteApi() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()

            every { localRepo.getFavorites() } returns flowOf(emptyList())
            every { localRepo.getTopPlayedTracks(any()) } returns flowOf(emptyList())
            every { localRepo.getPlaylists() } returns flowOf(emptyList())
            every { localRepo.getTotalPlayCount() } returns flowOf(0)
            every { localRepo.getTotalListenDurationMs() } returns flowOf(0L)

            val viewModel = LibraryViewModel(localRepo, onlineRepo)
            viewModel.showImportDialog(true)
            viewModel.importPlaylist(
                platform = Platform.QQ,
                rawInput = "这玩意儿不是链接",
                customName = ""
            )

            advanceUntilIdle()

            coVerify(exactly = 0) { onlineRepo.getPlaylistDetail(any(), any()) }
            coVerify(exactly = 0) { localRepo.createPlaylist(any()) }
            coVerify(exactly = 0) { localRepo.addAllToPlaylist(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun extractImportedPlaylistIdSupportsCommonShareLinks() {
        assertEquals(
            "9209322004",
            extractImportedPlaylistId(
                Platform.QQ,
                "https://y.qq.com/n/ryqq/playlist/9209322004"
            )
        )
        assertEquals(
            "19723756",
            extractImportedPlaylistId(
                Platform.NETEASE,
                "https://music.163.com/#/playlist?id=19723756"
            )
        )
        assertEquals(
            "2891238463",
            extractImportedPlaylistId(
                Platform.KUWO,
                "https://www.kuwo.cn/playlist_detail/2891238463"
            )
        )
    }
}
