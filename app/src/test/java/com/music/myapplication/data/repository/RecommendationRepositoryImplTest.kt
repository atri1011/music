package com.music.myapplication.data.repository

import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.ToplistInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun extractNeteaseRecommendedPlaylists_readsPlaylistCards() {
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "result": [
                {
                  "id": 748209731,
                  "name": "今日推荐",
                  "picUrl": "https://p2.music.126.net/cover.jpg",
                  "copywriter": "按你的口味精选"
                }
              ]
            }
            """.trimIndent()
        )

        val playlists = extractNeteaseRecommendedPlaylists(payload)

        assertEquals(1, playlists.size)
        assertEquals("748209731", playlists.first().id)
        assertEquals("今日推荐", playlists.first().name)
        assertEquals("https://p2.music.126.net/cover.jpg", playlists.first().coverUrl)
        assertEquals("按你的口味精选", playlists.first().description)
    }

    @Test
    fun extractNeteaseRecommendedPlaylists_skipsBrokenItems() {
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "result": [
                {
                  "id": "",
                  "name": "缺少 ID"
                },
                {
                  "id": 9527,
                  "name": ""
                }
              ]
            }
            """.trimIndent()
        )

        val playlists = extractNeteaseRecommendedPlaylists(payload)

        assertTrue(playlists.isEmpty())
    }

    @Test
    fun extractQqRecommendedPlaylists_readsPersonalizedCards() {
        val payload = json.parseToJsonElement(
            """
            {
              "result": 100,
              "data": {
                "list": [
                  {
                    "content_id": 8075336924,
                    "id": 0,
                    "title": "90后回忆 | MP3时代的流行歌曲",
                    "cover": "http://qpic.y.qq.com/music_cover/cover/300?n=1",
                    "rcmdtemplate": "编辑推荐",
                    "username": "听风"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val playlists = extractQqRecommendedPlaylists(payload)

        assertEquals(1, playlists.size)
        assertEquals("8075336924", playlists.first().id)
        assertEquals("90后回忆 | MP3时代的流行歌曲", playlists.first().name)
        assertEquals("https://qpic.y.qq.com/music_cover/cover/300?n=1", playlists.first().coverUrl)
        assertEquals("编辑推荐", playlists.first().description)
    }

    @Test
    fun extractQqNewSongs_readsSongList() {
        val payload = json.parseToJsonElement(
            """
            {
              "result": 100,
              "data": {
                "list": [
                  {
                    "mid": "004BV00w11Ysvm",
                    "name": "我想",
                    "singer": [{"name": "刘宇宁"}],
                    "album": {
                      "id": 94236744,
                      "mid": "002lkcEK0ztfwR",
                      "name": "我想"
                    },
                    "interval": 212
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tracks = extractQqNewSongs(payload)

        assertEquals(1, tracks.size)
        assertEquals("004BV00w11Ysvm", tracks.first().id)
        assertEquals(Platform.QQ, tracks.first().platform)
        assertEquals("我想", tracks.first().title)
        assertEquals("刘宇宁", tracks.first().artist)
        assertEquals("我想", tracks.first().album)
        assertTrue(tracks.first().coverUrl.contains("002lkcEK0ztfwR"))
        assertEquals(212_000L, tracks.first().durationMs)
    }

    @Test
    fun extractQqDailyRecommendedTracks_readsSonglist() {
        val payload = json.parseToJsonElement(
            """
            {
              "result": 100,
              "data": {
                "songlist": [
                  {
                    "songmid": "0039MnYb0qxYhV",
                    "songname": "晴天",
                    "singer": [
                      {"name": "周杰伦"}
                    ],
                    "album": {
                      "id": 8220,
                      "mid": "000MkMni19ClKG",
                      "name": "叶惠美"
                    },
                    "interval": 269
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tracks = extractQqDailyRecommendedTracks(payload)

        assertEquals(1, tracks.size)
        assertEquals("0039MnYb0qxYhV", tracks.first().id)
        assertEquals(Platform.QQ, tracks.first().platform)
        assertEquals("晴天", tracks.first().title)
        assertEquals("周杰伦", tracks.first().artist)
        assertEquals("叶惠美", tracks.first().album)
        assertEquals(269_000L, tracks.first().durationMs)
        assertTrue(tracks.first().coverUrl.contains("000MkMni19ClKG"))
    }

    @Test
    fun extractQqDailyRecommendedTracks_returnsEmptyWhenLoginRequired() {
        val payload = json.parseToJsonElement("""{"result":301,"errMsg":"未登录"}""")

        val tracks = extractQqDailyRecommendedTracks(payload)

        assertTrue(tracks.isEmpty())
    }

    @Test
    fun getDailyRecommendedTracks_prefersQqDailyApi() = runTest {
        val api = mockk<TuneHubApi>()
        val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
        val localRepo = mockk<LocalLibraryRepository>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "result": 100,
              "data": {
                "songlist": [
                  {
                    "songmid": "daily-1",
                    "songname": "每日歌曲",
                    "singer": [{"name": "每日歌手"}],
                    "albummid": "album-mid-1",
                    "albumname": "每日专辑",
                    "interval": 180
                  },
                  {
                    "songmid": "daily-2",
                    "songname": "第二首",
                    "singer": [{"name": "歌手二"}]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        coEvery { api.fetchJsonElement(QQ_DAILY_RECOMMENDATION_URL) } returns payload
        coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val tracks = repository.getDailyRecommendedTracks(limit = 1)

        assertEquals(listOf("daily-1"), tracks.map { it.id })
        assertEquals(Platform.QQ, tracks.first().platform)
        coVerify(exactly = 0) { localRepo.getFavorites() }
        coVerify(exactly = 0) { onlineRepo.search(any(), any(), any(), any()) }
    }

    @Test
    fun getDailyRecommendedTracks_prefersLocalTasteAndFiltersKnownSongs() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val onlineRepo = mockk<OnlineMusicRepository>()
        val localRepo = mockk<LocalLibraryRepository>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val favorite = testTrack(id = "fav-1", artist = "周杰伦")
        val recent = testTrack(id = "recent-1", artist = "林俊杰", platform = Platform.QQ)
        val recommendation = testTrack(id = "new-1", artist = "周杰伦")
        val recentRecommendation = testTrack(id = "new-2", artist = "林俊杰", platform = Platform.QQ)

        every { localRepo.getFavorites() } returns flowOf(listOf(favorite))
        every { localRepo.getRecentPlays(any()) } returns flowOf(listOf(recent))
        coEvery { localRepo.getTrackPlayCount(favorite.id, favorite.platform.id) } returns 5
        coEvery { localRepo.getTrackPlayCount(recent.id, recent.platform.id) } returns 3
        coEvery { localRepo.getFirstPlayDate(any(), any()) } returns System.currentTimeMillis() - (40L * 24 * 60 * 60 * 1000)
        coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
        coEvery { api.fetchJsonElement(QQ_DAILY_RECOMMENDATION_URL) } returns json.parseToJsonElement(
            """{"result":301,"errMsg":"未登录"}"""
        )
        coEvery { onlineRepo.search(Platform.NETEASE, "周杰伦", 1, any()) } returns Result.Success(
            listOf(favorite, recommendation)
        )
        coEvery { onlineRepo.search(Platform.QQ, "林俊杰", 1, any()) } returns Result.Success(
            listOf(recent, recentRecommendation)
        )

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val tracks = repository.getDailyRecommendedTracks(limit = 5)

        assertEquals(listOf("new-1", "new-2"), tracks.take(2).map { it.id })
        assertTrue("fav-1" in tracks.map { it.id })
        assertTrue("recent-1" in tracks.map { it.id })
        coVerify(exactly = 0) { onlineRepo.getToplists(any()) }
    }

    @Test
    fun getDailyRecommendedTracks_fallsBackToLocalSeedsWhenNoOnlineMatch() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val onlineRepo = mockk<OnlineMusicRepository>()
        val localRepo = mockk<LocalLibraryRepository>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val favorite = testTrack(id = "fav-1", artist = "Aimer")
        val recent = testTrack(id = "recent-1", artist = "宇多田ヒカル")

        every { localRepo.getFavorites() } returns flowOf(listOf(favorite))
        every { localRepo.getRecentPlays(any()) } returns flowOf(listOf(recent))
        coEvery { localRepo.getTrackPlayCount(any(), any()) } returns 2
        coEvery { localRepo.getFirstPlayDate(any(), any()) } returns null
        coEvery { localRepo.applyFavoriteState(any()) } answers { firstArg() }
        coEvery { api.fetchJsonElement(QQ_DAILY_RECOMMENDATION_URL) } returns json.parseToJsonElement(
            """{"result":301,"errMsg":"未登录"}"""
        )
        coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val tracks = repository.getDailyRecommendedTracks(limit = 5)

        assertEquals(listOf("fav-1", "recent-1"), tracks.map { it.id })
        assertTrue(tracks.first().isFavorite)
    }

    @Test
    fun getDailyRecommendedTracks_usesColdStartWhenNoLocalTaste() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val onlineRepo = mockk<OnlineMusicRepository>()
        val localRepo = mockk<LocalLibraryRepository>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val toplistTrack = testTrack(id = "top-1", artist = "歌手")

        every { localRepo.getFavorites() } returns flowOf(emptyList())
        every { localRepo.getRecentPlays(any()) } returns flowOf(emptyList())
        coEvery { api.fetchJsonElement(QQ_DAILY_RECOMMENDATION_URL) } returns json.parseToJsonElement(
            """{"result":301,"errMsg":"未登录"}"""
        )
        coEvery { onlineRepo.getToplists(Platform.NETEASE) } returns Result.Success(
            listOf(ToplistInfo(id = "19723756", name = "飙升榜"))
        )
        coEvery { onlineRepo.getToplistDetail(Platform.NETEASE, "19723756") } returns Result.Success(
            listOf(toplistTrack)
        )

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val tracks = repository.getDailyRecommendedTracks(limit = 5)

        assertEquals(listOf("top-1"), tracks.map { it.id })
        coVerify(exactly = 1) { onlineRepo.getToplists(Platform.NETEASE) }
    }

    @Test
    fun getGuessYouLikeTracks_fallsBackToColdStartWhenSearchHasNoResults() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val onlineRepo = mockk<OnlineMusicRepository>()
        val localRepo = mockk<LocalLibraryRepository>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val favorite = testTrack(id = "fav-1", artist = "Aimer").copy(isFavorite = true)
        val recent = testTrack(id = "recent-1", artist = "宇多田ヒカル", platform = Platform.QQ)
        val hotTrack1 = testTrack(id = "hot-1", artist = "热门歌手")
        val hotTrack2 = testTrack(id = "hot-2", artist = "热门歌手")

        every { localRepo.getFavorites() } returns flowOf(listOf(favorite))
        every { localRepo.getRecentPlays(any()) } returns flowOf(listOf(recent))
        coEvery { localRepo.getTrackPlayCount(any(), any()) } returns 1
        coEvery { localRepo.getFirstPlayDate(any(), any()) } returns null
        coEvery { onlineRepo.search(any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { onlineRepo.getToplists(Platform.NETEASE) } returns Result.Success(
            listOf(ToplistInfo(id = "19723756", name = "飙升榜"))
        )
        coEvery { onlineRepo.getToplistDetail(Platform.NETEASE, "19723756") } returns Result.Success(
            listOf(hotTrack1, hotTrack2)
        )

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val result = repository.getGuessYouLikeTracks(refreshCount = 0, limit = 6)

        assertEquals("热门推荐", result.label)
        assertEquals(listOf("hot-1", "hot-2"), result.tracks.map { it.id })
        coVerify(exactly = 1) { onlineRepo.getToplists(Platform.NETEASE) }
    }

    @Test
    fun getRecommendedPlaylists_returnsCachedDataWithoutRequest() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
        val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>()
        val cached = listOf(ToplistInfo(id = "1", name = "今日推荐"))
        coEvery { cacheStore.getCachedRecommendedPlaylists() } returns cached

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val playlists = repository.getRecommendedPlaylists()

        assertEquals(cached, playlists)
        coVerify(exactly = 0) { api.getNeteaseRecommendedPlaylists(any(), any()) }
    }

    @Test
    fun getRecommendedPlaylists_cachesFreshResponse() = runTest {
        val api = mockk<TuneHubApi>()
        val onlineRepo = mockk<OnlineMusicRepository>(relaxed = true)
        val localRepo = mockk<LocalLibraryRepository>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>()
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "result": [
                {
                  "id": 748209731,
                  "name": "今日推荐",
                  "picUrl": "https://p2.music.126.net/cover.jpg",
                  "copywriter": "按你的口味精选"
                }
              ]
            }
            """.trimIndent()
        )
        coEvery { cacheStore.getCachedRecommendedPlaylists() } returns null
        coEvery { api.getNeteaseRecommendedPlaylists(any(), any()) } returns payload
        coEvery { cacheStore.cacheRecommendedPlaylists(any()) } returns Unit

        val repository = RecommendationRepositoryImpl(api, onlineRepo, localRepo, cacheStore)

        val playlists = repository.getRecommendedPlaylists()

        assertEquals(1, playlists.size)
        coVerify(exactly = 1) {
            cacheStore.cacheRecommendedPlaylists(
                listOf(
                    ToplistInfo(
                        id = "748209731",
                        name = "今日推荐",
                        coverUrl = "https://p2.music.126.net/cover.jpg",
                        description = "按你的口味精选"
                    )
                )
            )
        }
    }

    private fun testTrack(
        id: String,
        artist: String,
        platform: Platform = Platform.NETEASE
    ) = Track(
        id = id,
        platform = platform,
        title = "测试歌曲-$id",
        artist = artist
    )
}
