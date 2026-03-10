package com.music.myapplication.feature.library

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.download.DownloadManager
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
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private fun createDownloadManagerMock(): DownloadManager {
        val dm = mockk<DownloadManager>()
        every { dm.getDownloadedCount() } returns flowOf(0)
        return dm
    }

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
            every { localRepo.getLocalTrackCount() } returns flowOf(0)
            coEvery { onlineRepo.getPlaylistDetail(Platform.NETEASE, "19723756") } returns Result.Success(importedTracks)
            coEvery { localRepo.createPlaylist("夜曲合集") } returns Playlist(id = "local-1", name = "夜曲合集")
            coEvery { localRepo.addAllToPlaylist("local-1", importedTracks) } returns Unit

            val viewModel = LibraryViewModel(localRepo, onlineRepo, createDownloadManagerMock())
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
            assertEquals("local-1", viewModel.state.value.importedPlaylist?.playlistId)
            assertEquals("夜曲合集", viewModel.state.value.importedPlaylist?.playlistName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importPlaylistResolvesQqShortShareLinkBeforeImport() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val playlistsFlow = MutableStateFlow(emptyList<Playlist>())
            val importedTracks = listOf(
                Track(
                    id = "0039MnYb0qxYhV",
                    platform = Platform.QQ,
                    title = "晴天",
                    artist = "周杰伦"
                )
            )
            val shortUrl = "https://c6.y.qq.com/base/fcgi-bin/u?__=Hvvmr33vDHrY"
            val resolvedUrl = "https://i.y.qq.com/n2/m/share/details/taoge.html?id=9601259329"

            every { localRepo.getFavorites() } returns flowOf(emptyList())
            every { localRepo.getTopPlayedTracks(any()) } returns flowOf(emptyList())
            every { localRepo.getPlaylists() } returns playlistsFlow
            every { localRepo.getTotalPlayCount() } returns flowOf(0)
            every { localRepo.getTotalListenDurationMs() } returns flowOf(0L)
            every { localRepo.getLocalTrackCount() } returns flowOf(0)
            coEvery { onlineRepo.resolveShareUrl(shortUrl) } returns resolvedUrl
            coEvery { onlineRepo.getPlaylistDetail(Platform.QQ, "9601259329") } returns Result.Success(importedTracks)
            coEvery { localRepo.createPlaylist("QQ收藏") } returns Playlist(id = "local-qq-1", name = "QQ收藏")
            coEvery { localRepo.addAllToPlaylist("local-qq-1", importedTracks) } returns Unit

            val viewModel = LibraryViewModel(localRepo, onlineRepo, createDownloadManagerMock())
            viewModel.showImportDialog(true)
            viewModel.importPlaylist(
                platform = Platform.QQ,
                rawInput = shortUrl,
                customName = "QQ收藏"
            )

            advanceUntilIdle()

            coVerify(exactly = 1) { onlineRepo.resolveShareUrl(shortUrl) }
            coVerify(exactly = 1) { onlineRepo.getPlaylistDetail(Platform.QQ, "9601259329") }
            coVerify(exactly = 1) { localRepo.createPlaylist("QQ收藏") }
            coVerify(exactly = 1) { localRepo.addAllToPlaylist("local-qq-1", importedTracks) }
            assertEquals("local-qq-1", viewModel.state.value.importedPlaylist?.playlistId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importPlaylistResolvesShortShareLinkInsideMobileShareText() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val onlineRepo = mockk<OnlineMusicRepository>()
            val localRepo = mockk<LocalLibraryRepository>()
            val playlistsFlow = MutableStateFlow(emptyList<Playlist>())
            val importedTracks = listOf(
                Track(
                    id = "0039MnYb0qxYhV",
                    platform = Platform.QQ,
                    title = "晴天",
                    artist = "周杰伦"
                )
            )
            val shareText = "我发现一张不错的歌单，分享给你 @QQ音乐 https://c6.y.qq.com/base/fcgi-bin/u?__=Hvvmr33vDHrY，复制这条消息查看详情"
            val resolvedUrl = "https://i.y.qq.com/n2/m/share/details/taoge.html?id=9601259329"

            every { localRepo.getFavorites() } returns flowOf(emptyList())
            every { localRepo.getTopPlayedTracks(any()) } returns flowOf(emptyList())
            every { localRepo.getPlaylists() } returns playlistsFlow
            every { localRepo.getTotalPlayCount() } returns flowOf(0)
            every { localRepo.getTotalListenDurationMs() } returns flowOf(0L)
            every { localRepo.getLocalTrackCount() } returns flowOf(0)
            coEvery { onlineRepo.resolveShareUrl(shareText) } returns resolvedUrl
            coEvery { onlineRepo.getPlaylistDetail(Platform.QQ, "9601259329") } returns Result.Success(importedTracks)
            coEvery { localRepo.createPlaylist("手机QQ收藏") } returns Playlist(id = "local-qq-mobile-1", name = "手机QQ收藏")
            coEvery { localRepo.addAllToPlaylist("local-qq-mobile-1", importedTracks) } returns Unit

            val viewModel = LibraryViewModel(localRepo, onlineRepo, createDownloadManagerMock())
            viewModel.showImportDialog(true)
            viewModel.importPlaylist(
                platform = Platform.QQ,
                rawInput = shareText,
                customName = "手机QQ收藏"
            )

            advanceUntilIdle()

            coVerify(exactly = 1) { onlineRepo.resolveShareUrl(shareText) }
            coVerify(exactly = 1) { onlineRepo.getPlaylistDetail(Platform.QQ, "9601259329") }
            coVerify(exactly = 1) { localRepo.createPlaylist("手机QQ收藏") }
            coVerify(exactly = 1) { localRepo.addAllToPlaylist("local-qq-mobile-1", importedTracks) }
            assertEquals("local-qq-mobile-1", viewModel.state.value.importedPlaylist?.playlistId)
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
            every { localRepo.getLocalTrackCount() } returns flowOf(0)

            val viewModel = LibraryViewModel(localRepo, onlineRepo, createDownloadManagerMock())
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

    @Test
    fun resolveImportedPlaylistInputSupportsEmbeddedMobileShareText() {
        val qqResolved = resolveImportedPlaylistInput(
            selectedPlatform = Platform.NETEASE,
            rawInput = "分享歌单给你 @QQ音乐 https://i.y.qq.com/n2/m/share/details/taoge.html?id=9601259329，复制查看"
        )
        assertEquals(Platform.QQ, qqResolved?.platform)
        assertEquals("9601259329", qqResolved?.playlistId)

        val neteaseResolved = resolveImportedPlaylistInput(
            selectedPlatform = Platform.QQ,
            rawInput = "网易云音乐分享 https://y.music.163.com/m/playlist?id=19723756&uct2=foo。"
        )
        assertEquals(Platform.NETEASE, neteaseResolved?.platform)
        assertEquals("19723756", neteaseResolved?.playlistId)

        val kuwoResolved = resolveImportedPlaylistInput(
            selectedPlatform = Platform.QQ,
            rawInput = "【酷我音乐】歌单推荐 https://h5app.kuwo.cn/www/playlist?pid=2891238463&from=vip。"
        )
        assertEquals(Platform.KUWO, kuwoResolved?.platform)
        assertEquals("2891238463", kuwoResolved?.playlistId)
    }

    @Test
    fun extractImportedPlaylistIdRejectsObviousSongLink() {
        assertNull(
            extractImportedPlaylistId(
                Platform.QQ,
                "https://y.qq.com/n/ryqq/songDetail/123456"
            )
        )
    }

    @Test
    fun resolveImportedPlaylistInputAutoDetectsPlatformFromLink() {
        val resolved = resolveImportedPlaylistInput(
            selectedPlatform = Platform.QQ,
            rawInput = "https://music.163.com/#/playlist?id=19723756"
        )

        assertEquals(Platform.NETEASE, resolved?.platform)
        assertEquals("19723756", resolved?.playlistId)
    }
}
