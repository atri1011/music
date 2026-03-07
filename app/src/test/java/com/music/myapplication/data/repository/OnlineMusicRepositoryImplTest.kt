package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.network.dispatch.DispatchExecutor
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class OnlineMusicRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun getToplists_returnsCachedDataWithoutDispatch() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val dispatchExecutor = mockk<DispatchExecutor>()
        val cacheStore = mockk<HomeContentCacheStore>()
        val cached = listOf(ToplistInfo(id = "19723756", name = "飙升榜"))
        coEvery { cacheStore.getCachedToplists(Platform.NETEASE) } returns cached

        val repository = OnlineMusicRepositoryImpl(api, dispatchExecutor, json, cacheStore)

        val result = repository.getToplists(Platform.NETEASE)

        assertEquals(cached, (result as Result.Success).data)
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun getToplistDetailFast_returnsCachedTracksWithoutNetwork() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val dispatchExecutor = mockk<DispatchExecutor>()
        val cacheStore = mockk<HomeContentCacheStore>()
        val cachedTracks = listOf(
            Track(
                id = "123",
                platform = Platform.NETEASE,
                title = "缓存歌曲",
                artist = "缓存歌手"
            ),
            Track(
                id = "456",
                platform = Platform.NETEASE,
                title = "缓存歌曲2",
                artist = "缓存歌手2"
            )
        )
        coEvery { cacheStore.getCachedToplistDetail(Platform.NETEASE, "19723756") } returns cachedTracks

        val repository = OnlineMusicRepositoryImpl(api, dispatchExecutor, json, cacheStore)

        val result = repository.getToplistDetailFast(Platform.NETEASE, "19723756")

        assertEquals(cachedTracks, (result as Result.Success).data)
        coVerify(exactly = 0) { api.getNeteasePlaylistDetailV6(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun getToplistDetailFast_bypassesBrokenSingleTrackCacheForNetease() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>()
        val brokenCache = listOf(
            Track(
                id = "123",
                platform = Platform.NETEASE,
                title = "缓存歌曲",
                artist = "缓存歌手"
            )
        )
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "playlist": {
                "tracks": [
                  {
                    "id": 123,
                    "name": "第一首",
                    "dt": 210000,
                    "ar": [{ "name": "歌手A" }],
                    "al": { "name": "专辑A", "picUrl": "https://example.com/a.jpg" }
                  },
                  {
                    "id": 456,
                    "name": "第二首",
                    "dt": 220000,
                    "ar": [{ "name": "歌手B" }],
                    "al": { "name": "专辑B", "picUrl": "https://example.com/b.jpg" }
                  }
                ],
                "trackIds": [
                  { "id": 123 },
                  { "id": 456 }
                ]
              }
            }
            """.trimIndent()
        )
        coEvery { cacheStore.getCachedToplistDetail(Platform.NETEASE, "19723756") } returns brokenCache
        coEvery { api.getNeteasePlaylistDetailV6("19723756", any(), any(), any()) } returns payload
        coEvery { cacheStore.cacheToplistDetail(Platform.NETEASE, "19723756", any()) } returns Unit

        val repository = OnlineMusicRepositoryImpl(api, dispatchExecutor, json, cacheStore)

        val result = repository.getToplistDetailFast(Platform.NETEASE, "19723756")

        assertEquals(listOf("123", "456"), (result as Result.Success).data.map { it.id })
        coVerify(exactly = 1) { api.getNeteasePlaylistDetailV6("19723756", any(), any(), any()) }
        coVerify(exactly = 1) { cacheStore.cacheToplistDetail(Platform.NETEASE, "19723756", any()) }
    }

    @Test
    fun extractNeteaseSongCoverMap_readsAlbumPicUrl() {
        val payload = json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": 123,
                  "album": {
                    "picUrl": "https://example.com/cover-a.jpg"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val coverMap = extractNeteaseSongCoverMap(payload)

        assertEquals("https://example.com/cover-a.jpg", coverMap["123"])
    }

    @Test
    fun extractNeteaseSongCoverMap_supportsAlFieldFallback() {
        val payload = json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": "456",
                  "al": {
                    "picUrl": "https://example.com/cover-b.jpg"
                  }
                },
                {
                  "id": "789",
                  "name": "no-cover"
                }
              ]
            }
            """.trimIndent()
        )

        val coverMap = extractNeteaseSongCoverMap(payload)

        assertEquals("https://example.com/cover-b.jpg", coverMap["456"])
        assertTrue("789" !in coverMap)
    }

    @Test
    fun extractNeteasePlaylistTracks_readsV6PlaylistStructure() {
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "playlist": {
                "tracks": [
                  {
                    "id": 123,
                    "name": "晴天",
                    "dt": 269000,
                    "ar": [
                      { "name": "周杰伦" },
                      { "name": "杨瑞代" }
                    ],
                    "al": {
                      "name": "叶惠美",
                      "picUrl": "https://example.com/qingtian.jpg"
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val tracks = extractNeteasePlaylistTracks(payload)

        assertEquals(1, tracks.size)
        assertEquals("123", tracks.first().id)
        assertEquals("晴天", tracks.first().title)
        assertEquals("周杰伦/杨瑞代", tracks.first().artist)
        assertEquals("叶惠美", tracks.first().album)
        assertEquals("https://example.com/qingtian.jpg", tracks.first().coverUrl)
        assertEquals(269000L, tracks.first().durationMs)
    }

    @Test
    fun extractNeteasePlaylistTrackIds_readsTrackIdsWhenTracksAreIncomplete() {
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "playlist": {
                "trackIds": [
                  { "id": 123 },
                  { "id": 456 },
                  { "id": 123 }
                ]
              }
            }
            """.trimIndent()
        )

        val trackIds = extractNeteasePlaylistTrackIds(payload)

        assertEquals(listOf("123", "456"), trackIds)
    }

    @Test
    fun extractNeteaseSongTracks_readsSongDetailStructure() {
        val payload = json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": 321,
                  "name": "稻香",
                  "dt": 223000,
                  "artists": [
                    { "name": "周杰伦" }
                  ],
                  "album": {
                    "name": "魔杰座",
                    "picUrl": "https://example.com/daoxiang.jpg"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val tracks = extractNeteaseSongTracks(payload)

        assertEquals(1, tracks.size)
        assertEquals("321", tracks.first().id)
        assertEquals("稻香", tracks.first().title)
        assertEquals("周杰伦", tracks.first().artist)
        assertEquals("魔杰座", tracks.first().album)
        assertEquals("https://example.com/daoxiang.jpg", tracks.first().coverUrl)
        assertEquals(223000L, tracks.first().durationMs)
    }

    @Test
    fun extractQqSongCoverMap_buildsAlbumCoverUrlFromAlbumMid() {
        val payload = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 97773,
                  "mid": "0039MnYb0qxYhV",
                  "album": { "mid": "000MkMni19ClKG" }
                }
              ]
            }
            """.trimIndent()
        )

        val coverMap = extractQqSongCoverMap(payload)

        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000000MkMni19ClKG.jpg",
            coverMap["0039MnYb0qxYhV"]
        )
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000000MkMni19ClKG.jpg",
            coverMap["97773"]
        )
    }

    @Test
    fun extractQqToplists_readsHeadPicAndFrontPic() {
        val payload = json.parseToJsonElement(
            """
            {
              "toplist": {
                "data": {
                  "group": [
                    {
                      "toplist": [
                        {
                          "topId": 26,
                          "title": "热歌榜",
                          "headPicUrl": "http://example.com/head.jpg"
                        },
                        {
                          "topId": 62,
                          "title": "飙升榜",
                          "frontPicUrl": "//example.com/front.jpg"
                        }
                      ]
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val toplists = extractQqToplists(payload)

        assertEquals(2, toplists.size)
        assertEquals("26", toplists[0].id)
        assertEquals("https://example.com/head.jpg", toplists[0].coverUrl)
        assertEquals("62", toplists[1].id)
        assertEquals("https://example.com/front.jpg", toplists[1].coverUrl)
    }

    @Test
    fun extractQqToplistSongCoverMap_readsSongInfoListAlbumMid() {
        val payload = json.parseToJsonElement(
            """
            {
              "toplist": {
                "data": {
                  "songInfoList": [
                    {
                      "id": 645177322,
                      "mid": "002Kgaz04dXb1Z",
                      "album": { "mid": "001v8kTz1j0oul" }
                    },
                    {
                      "id": 496054946,
                      "mid": "001auUcH4WQs2V",
                      "album": { "mid": "004HaG7p4ZkhXA" }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val coverMap = extractQqToplistSongCoverMap(payload)

        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000001v8kTz1j0oul.jpg",
            coverMap["002Kgaz04dXb1Z"]
        )
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000001v8kTz1j0oul.jpg",
            coverMap["645177322"]
        )
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000004HaG7p4ZkhXA.jpg",
            coverMap["001auUcH4WQs2V"]
        )
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000004HaG7p4ZkhXA.jpg",
            coverMap["496054946"]
        )
    }

    @Test
    fun extractKuwoSongCoverMap_parsesRidResponseAndNormalizesUrl() {
        val payload = """
            {'abslist':[
              {'id':'228908','web_albumpic_short':'120/s3s94/93/211513640.jpg'},
              {'id':'530900521','web_albumpic_short':'https://img2.kuwo.cn/star/albumcover/120/s4s11/92/707175307.jpg'}
            ]}
        """.trimIndent()

        val coverMap = extractKuwoSongCoverMap(payload, json)

        assertEquals(
            "https://img4.kuwo.cn/star/albumcover/120/s3s94/93/211513640.jpg",
            coverMap["228908"]
        )
        assertEquals(
            "https://img2.kuwo.cn/star/albumcover/120/s4s11/92/707175307.jpg",
            coverMap["530900521"]
        )
    }
}
