package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.network.dispatch.DispatchExecutor
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.remote.dto.ParseResponseDto
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class OnlineMusicRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun getHotSearchKeywords_forNetease_prefersDetailEndpoint() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": [
                { "searchWord": "晴天" },
                { "searchWord": "夜曲" }
              ]
            }
            """.trimIndent()
        )
        coEvery { api.getNeteaseHotSearchDetail(any()) } returns payload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getHotSearchKeywords(Platform.NETEASE)

        assertEquals(listOf("晴天", "夜曲"), (result as Result.Success).data)
        coVerify(exactly = 1) { api.getNeteaseHotSearchDetail(any()) }
        coVerify(exactly = 0) { api.getNeteaseHotSearch(any()) }
    }

    @Test
    fun getHotSearchKeywords_forNetease_fallsBackToLegacyEndpoint() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val emptyDetailPayload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": []
            }
            """.trimIndent()
        )
        val legacyPayload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "result": {
                "hots": [
                  { "first": "稻香" },
                  { "first": "七里香" }
                ]
              }
            }
            """.trimIndent()
        )
        coEvery { api.getNeteaseHotSearchDetail(any()) } returns emptyDetailPayload
        coEvery { api.getNeteaseHotSearch(any()) } returns legacyPayload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getHotSearchKeywords(Platform.NETEASE)

        assertEquals(listOf("稻香", "七里香"), (result as Result.Success).data)
        coVerify(exactly = 1) { api.getNeteaseHotSearchDetail(any()) }
        coVerify(exactly = 1) { api.getNeteaseHotSearch(any()) }
    }

    @Test
    fun getHotSearchKeywords_forNetease_fallsBackToDefaultKeywordAndToplistTracks() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>()
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val defaultKeywordPayload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": {
                "realkeyword": "海屿你"
              }
            }
            """.trimIndent()
        )
        val cachedToplists = listOf(
            ToplistInfo(id = "19723756", name = "热歌榜"),
            ToplistInfo(id = "3779629", name = "新歌榜")
        )
        val cachedTracks = listOf(
            Track(id = "1", platform = Platform.NETEASE, title = "晴天", artist = "周杰伦"),
            Track(id = "2", platform = Platform.NETEASE, title = "七里香", artist = "周杰伦")
        )
        coEvery { api.getNeteaseHotSearchDetail(any()) } returns json.parseToJsonElement("""{"code":404}""")
        coEvery { api.getNeteaseHotSearch(any()) } returns json.parseToJsonElement("""{"code":400}""")
        coEvery { api.getNeteaseDefaultKeyword(any()) } returns defaultKeywordPayload
        coEvery { cacheStore.getCachedToplists(Platform.NETEASE) } returns cachedToplists
        coEvery { cacheStore.getCachedToplistDetail(Platform.NETEASE, "19723756") } returns cachedTracks

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getHotSearchKeywords(Platform.NETEASE)

        assertEquals(listOf("海屿你", "晴天", "七里香"), (result as Result.Success).data.take(3))
        coVerify(exactly = 1) { api.getNeteaseDefaultKeyword(any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(Platform.NETEASE, "toplists", any()) }
    }

    @Test
    fun getToplists_returnsCachedDataWithoutDispatch() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val dispatchExecutor = mockk<DispatchExecutor>()
        val cacheStore = mockk<HomeContentCacheStore>()
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val cached = listOf(ToplistInfo(id = "19723756", name = "飙升榜"))
        coEvery { cacheStore.getCachedToplists(Platform.NETEASE) } returns cached

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getToplists(Platform.NETEASE)

        assertEquals(cached, (result as Result.Success).data)
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun getToplistDetailFast_returnsCachedTracksWithoutNetwork() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val dispatchExecutor = mockk<DispatchExecutor>()
        val cacheStore = mockk<HomeContentCacheStore>()
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
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

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getToplistDetailFast(Platform.NETEASE, "19723756")

        assertEquals(cachedTracks, (result as Result.Success).data)
        coVerify(exactly = 0) { api.getNeteasePlaylistDetailV6(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun getToplistDetailFast_bypassesBrokenSingleTrackCacheForNetease() = runTest {
        val api = mockk<TuneHubApi>()
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
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

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getToplistDetailFast(Platform.NETEASE, "19723756")

        assertEquals(listOf("123", "456"), (result as Result.Success).data.map { it.id })
        coVerify(exactly = 1) { api.getNeteasePlaylistDetailV6("19723756", any(), any(), any()) }
        coVerify(exactly = 1) { cacheStore.cacheToplistDetail(Platform.NETEASE, "19723756", any()) }
    }

    @Test
    fun resolveShareUrl_returnsRedirectTargetRequestUrl() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val redirectTarget = "https://y.qq.com/n/ryqq/playlist/9601259329"
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(Request.Builder().url(redirectTarget).build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val resolved = repository.resolveShareUrl("https://c6.y.qq.com/base/fcgi-bin/u?__=Hvvmr33vDHrY")

        assertEquals(redirectTarget, resolved)
    }

    @Test
    fun resolveVideoUrl_prefersTuneHubVideoPayload() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        coEvery {
            api.parse(any(), any())
        } returns ParseResponseDto(
            code = 0,
            data = json.parseToJsonElement("""{"videoUrl":"https://example.com/video.m3u8"}""")
        )

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.resolveVideoUrl(
            Track(
                id = "0039MnYb0qxYhV",
                platform = Platform.QQ,
                title = "晴天",
                artist = "周杰伦"
            )
        )

        assertEquals("https://example.com/video.m3u8", (result as Result.Success).data)
        coVerify(exactly = 1) { api.parse(any(), any()) }
        coVerify(exactly = 0) { api.getQqSongDetail(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun resolveVideoUrl_forNetease_readsMvDetailUrl() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        coEvery { api.parse(any(), any()) } returns ParseResponseDto(
            code = 0,
            data = json.parseToJsonElement("""{}""")
        )
        coEvery { api.getNeteaseSongDetail("[123]", any()) } returns json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": 123,
                  "mv": 556677
                }
              ]
            }
            """.trimIndent()
        )
        coEvery { api.getNeteaseMvDetail("556677", any(), any()) } returns json.parseToJsonElement(
            """
            {
              "data": {
                "brs": {
                  "720": "https://example.com/video-720.mp4"
                }
              }
            }
            """.trimIndent()
        )

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.resolveVideoUrl(
            Track(
                id = "123",
                platform = Platform.NETEASE,
                title = "稻香",
                artist = "周杰伦"
            )
        )

        assertEquals("https://example.com/video-720.mp4", (result as Result.Success).data)
        coVerify(exactly = 2) { api.parse(any(), any()) }
        coVerify(exactly = 1) { api.getNeteaseSongDetail("[123]", any()) }
        coVerify(exactly = 1) { api.getNeteaseMvDetail("556677", any(), any()) }
    }

    @Test
    fun resolveVideoUrl_forQq_readsMvVidAndUrl() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val bodySlot = slot<JsonElement>()
        coEvery { api.parse(any(), any()) } returns ParseResponseDto(
            code = 0,
            data = json.parseToJsonElement("""{}""")
        )
        coEvery {
            api.getQqSongDetail("0039MnYb0qxYhV", any(), any(), any(), any(), any())
        } returns json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "mid": "0039MnYb0qxYhV",
                  "mv": {
                    "vid": "m0034testvid"
                  }
                }
              ]
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } answers {
            bodySlot.captured = firstArg()
            json.parseToJsonElement(
                """
                {
                  "mvUrl": {
                    "data": {
                      "m0034testvid": {
                        "freeflow_url": [
                          "http://mv.example.com/test.mp4"
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.resolveVideoUrl(
            Track(
                id = "0039MnYb0qxYhV",
                platform = Platform.QQ,
                title = "晴天",
                artist = "周杰伦"
            )
        )

        assertEquals("http://mv.example.com/test.mp4", (result as Result.Success).data)
        val root = bodySlot.captured as JsonObject
        val mvInfo = root["mvInfo"] as JsonObject
        val mvUrl = root["mvUrl"] as JsonObject
        assertEquals("video.VideoDataServer", (mvInfo["module"] as JsonPrimitive).content)
        assertEquals("music.stream.MvUrlProxy", (mvUrl["module"] as JsonPrimitive).content)
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
    fun getPlaylistCategories_forQq_usesPlayableCategories() = runTest {
        val api = mockk<TuneHubApi>(relaxed = true)
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getPlaylistCategories(Platform.QQ)

        val data = (result as Result.Success).data
        assertEquals(
            listOf("流行", "经典", "轻音乐", "摇滚", "民谣", "电子", "嘻哈", "R&B", "古典"),
            data.map { it.name }
        )
        assertTrue(data.first().hot)
    }

    @Test
    fun getPlaylistsByCategory_forQq_usesValidCategoryRequest() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val requestBody = slot<JsonElement>()
        val payload = json.parseToJsonElement(
            """
            {
              "playlist": {
                "data": {
                  "v_playlist": [
                    {
                      "tid": 3261601343,
                      "title": "热情海岛风，从东方夏威夷吹来",
                      "cover_url_big": "http://p.qpic.cn/music_cover/example/600?n=1",
                      "access_num": 29305,
                      "desc": "测试歌单"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } answers {
            requestBody.captured = firstArg()
            payload
        }

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getPlaylistsByCategory(
            platform = Platform.QQ,
            category = "流行",
            page = 2,
            pageSize = 30
        )

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("3261601343", data.first().id)
        assertEquals("热情海岛风，从东方夏威夷吹来", data.first().name)
        assertEquals("https://p.qpic.cn/music_cover/example/600?n=1", data.first().coverUrl)
        assertEquals(29305L, data.first().playCount)
        assertEquals("测试歌单", data.first().description)
        assertEquals(Platform.QQ, data.first().platform)

        val root = requestBody.captured as JsonObject
        val params = ((root["playlist"] as JsonObject)["param"]) as JsonObject
        assertEquals(6, (params["id"] as JsonPrimitive).content.toInt())
        assertEquals(2, (params["curPage"] as JsonPrimitive).content.toInt())
        assertEquals(30, (params["size"] as JsonPrimitive).content.toInt())
        assertEquals(6, (params["titleid"] as JsonPrimitive).content.toInt())
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

    @Test
    fun getTrackComments_prefersNeteaseWhenCommentsExist() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val currentTrack = Track(
            id = "530900521",
            platform = Platform.KUWO,
            title = "晴天",
            artist = "周杰伦",
            coverUrl = "https://example.com/current.jpg"
        )
        val neteaseCandidate = Track(
            id = "185811",
            platform = Platform.NETEASE,
            title = "晴天",
            artist = "周杰伦",
            coverUrl = "https://example.com/netease.jpg"
        )
        val neteaseComments = json.parseToJsonElement(
            """
            {
              "code": 200,
              "total": 1,
              "hotComments": [
                {
                  "commentId": 9,
                  "content": "这歌一响，青春就回来了。",
                  "likedCount": 420,
                  "time": 1710000000000,
                  "user": {
                    "nickname": "老铁",
                    "avatarUrl": "https://example.com/avatar.jpg"
                  }
                }
              ],
              "comments": [
                {
                  "commentId": 1,
                  "content": "旧接口最新流里的评论。",
                  "likedCount": 42,
                  "time": 1710000000000,
                  "user": {
                    "nickname": "旧接口用户",
                    "avatarUrl": "https://example.com/legacy-avatar.jpg"
                  }
                }
              ]
            }
            """.trimIndent()
        )
        val latestComments = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": {
                "totalCount": 1,
                "comments": [
                  {
                    "commentId": 11,
                    "content": "最新评论这条是真的。",
                    "likedCount": 5,
                    "time": 1710000001000,
                    "user": {
                      "nickname": "最新用户",
                      "avatarUrl": "https://example.com/latest.jpg"
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val recommendedComments = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": {
                "totalCount": 1,
                "comments": [
                  {
                    "commentId": 12,
                    "content": "推荐区这条也是真的。",
                    "likedCount": 88,
                    "time": 1710000002000,
                    "user": {
                      "nickname": "推荐用户",
                      "avatarUrl": "https://example.com/recommend.jpg"
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        coEvery { dispatchExecutor.executeByMethod(Platform.NETEASE, "search", any()) } returns
            Result.Success(listOf(neteaseCandidate))
        coEvery { api.getNeteaseSongComments("185811", any(), any(), any()) } returns neteaseComments
        coEvery {
            api.getNeteaseSortedSongComments("185811", any(), any(), 3, any(), any())
        } returns latestComments
        coEvery {
            api.getNeteaseSortedSongComments("185811", any(), any(), 1, any(), any())
        } returns recommendedComments

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getTrackComments(currentTrack)

        val data = (result as Result.Success).data
        assertEquals(Platform.NETEASE, data.sourcePlatform)
        assertEquals(1, data.totalCount)
        assertEquals(1, data.comments.size)
        assertEquals("老铁", data.comments.first().authorName)
        assertEquals("最新评论这条是真的。", data.latestComments.first().content)
        assertEquals("推荐区这条也是真的。", data.recommendedComments.first().content)
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(Platform.QQ, "search", any()) }
    }

    @Test
    fun getTrackComments_fallsBackToQqWhenNeteaseHasNoComments() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>()
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val currentTrack = Track(
            id = "530900521",
            platform = Platform.KUWO,
            title = "晴天",
            artist = "周杰伦",
            coverUrl = "https://example.com/current.jpg"
        )
        val neteaseCandidate = Track(
            id = "185811",
            platform = Platform.NETEASE,
            title = "晴天",
            artist = "周杰伦",
            coverUrl = "https://example.com/netease.jpg"
        )
        val qqCandidate = Track(
            id = "002Kgaz04dXb1Z",
            platform = Platform.QQ,
            title = "晴天",
            artist = "周杰伦",
            coverUrl = "https://example.com/qq.jpg"
        )
        val emptyNeteaseComments = json.parseToJsonElement(
            """
            {
              "code": 200,
              "total": 0,
              "comments": []
            }
            """.trimIndent()
        )
        val qqSongDetail = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 97773,
                  "mid": "002Kgaz04dXb1Z",
                  "album": { "mid": "000MkMni19ClKG" }
                }
              ]
            }
            """.trimIndent()
        )
        val qqCommentsRaw = """
            MusicJsonCallback({
              "code": 0,
              "comment": {
                "commenttotal": 2,
                "commentlist": [
                  {
                    "rootcommentid": "1001",
                    "rootcommentcontent": "QQ 这条评论也挺能打。",
                    "praisenum": 7,
                    "time": 1710000000,
                    "userinfo": {
                      "nick": "企鹅用户",
                      "avatarurl": "https://example.com/qq-avatar.jpg"
                    }
                  }
                ]
              }
            });
        """.trimIndent()

        coEvery { dispatchExecutor.executeByMethod(Platform.NETEASE, "search", any()) } returns
            Result.Success(listOf(neteaseCandidate))
        coEvery { api.getNeteaseSongComments("185811", any(), any(), any()) } returns emptyNeteaseComments
        coEvery {
            api.getNeteaseSortedSongComments("185811", any(), any(), 3, any(), any())
        } returns json.parseToJsonElement("""{"code":200,"data":{"totalCount":0,"comments":[]}}""")
        coEvery {
            api.getNeteaseSortedSongComments("185811", any(), any(), 1, any(), any())
        } returns json.parseToJsonElement("""{"code":200,"data":{"totalCount":0,"comments":[]}}""")
        coEvery { dispatchExecutor.executeByMethod(Platform.QQ, "search", any()) } returns
            Result.Success(listOf(qqCandidate))
        coEvery { api.getQqSongDetail("002Kgaz04dXb1Z", any(), any(), any(), any(), any()) } returns qqSongDetail
        coEvery {
            api.getQqSongCommentsRaw(any(), "97773", any(), any(), any(), any(), any())
        } returns qqCommentsRaw.toResponseBody()

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getTrackComments(currentTrack)

        val data = (result as Result.Success).data
        assertEquals(Platform.QQ, data.sourcePlatform)
        assertEquals(2, data.totalCount)
        assertEquals(1, data.comments.size)
        assertEquals("企鹅用户", data.comments.first().authorName)
        assertEquals(1, data.latestComments.size)
        assertTrue(data.hotComments.isEmpty())
        assertTrue(data.recommendedComments.isEmpty())
    }
    @Test
    fun getLyrics_returnsEmptyLyricsAsSuccess() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        coEvery {
            api.parse(any(), any())
        } returns ParseResponseDto(
            code = 0,
            data = json.parseToJsonElement(
                """
                {
                  "lyric": "",
                  "trans": "副歌重复一遍"
                }
                """.trimIndent()
            )
        )

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getLyrics(Platform.QQ, "track-1")

        val data = (result as Result.Success).data
        assertEquals("", data.lyric)
        assertEquals("副歌重复一遍", data.translation)
    }

    @Test
    fun searchArtists_forNetease_usesOfficialApiInsteadOfDispatchTemplate() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "result": {
                "artists": [
                  {
                    "id": 6452,
                    "name": "周杰伦",
                    "picUrl": "https://example.com/jay.jpg",
                    "musicSize": 686
                  }
                ]
              }
            }
            """.trimIndent()
        )
        coEvery { api.searchNeteaseByType("周杰伦", 100, 0, 20, any(), any(), any()) } returns payload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchArtists(Platform.NETEASE, "周杰伦", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("6452", data.first().id)
        assertEquals("周杰伦", data.first().title)
        assertEquals("https://example.com/jay.jpg", data.first().coverUrl)
        assertEquals(686, data.first().trackCount)
        coVerify(exactly = 1) { api.searchNeteaseByType("周杰伦", 100, 0, 20, any(), any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun searchAlbums_forQq_usesOfficialApiInsteadOfDispatchTemplate() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val requestBody = slot<JsonElement>()
        val payload = json.parseToJsonElement(
            """
            {
              "req": {
                "data": {
                  "body": {
                    "direct_result": {
                      "items": [
                        {
                          "id": "8220",
                          "restype": "album",
                          "title": "叶惠美",
                          "pic": "http://y.gtimg.cn/music/photo_new/T002R180x180M000000MkMni19ClKG_5.jpg",
                          "custom_info": {
                            "mid": "000MkMni19ClKG",
                            "quality_album_title_prefix": "周杰伦",
                            "track_num": "11"
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } answers {
            requestBody.captured = firstArg()
            payload
        }

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchAlbums(Platform.QQ, "周杰伦", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("8220", data.first().id)
        assertEquals("叶惠美", data.first().title)
        assertEquals("周杰伦", data.first().subtitle)
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R180x180M000000MkMni19ClKG_5.jpg",
            data.first().coverUrl
        )
        assertEquals(11, data.first().trackCount)

        val root = requestBody.captured as JsonObject
        val params = ((root["req"] as JsonObject)["param"]) as JsonObject
        assertEquals(100, (params["search_type"] as JsonPrimitive).content.toInt())
        assertEquals("周杰伦", (params["query"] as JsonPrimitive).content)
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun searchArtists_forQq_usesGeneralSearchDirectResult() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "req": {
                "data": {
                  "body": {
                    "direct_result": {
                      "items": [
                        {
                          "id": "4558",
                          "restype": "singer",
                          "title": "周杰伦",
                          "pic": "http://y.gtimg.cn/music/photo_new/T001R150x150M0000025NhlN2yWrP4_10.jpg",
                          "custom_info": {
                            "mid": "0025NhlN2yWrP4",
                            "song_num": "997"
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } returns payload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchArtists(Platform.QQ, "周杰伦", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("0025NhlN2yWrP4", data.first().id)
        assertEquals("周杰伦", data.first().title)
        assertEquals("https://y.gtimg.cn/music/photo_new/T001R150x150M0000025NhlN2yWrP4_10.jpg", data.first().coverUrl)
        assertEquals(997, data.first().trackCount)
        coVerify(exactly = 1) { api.postQqMusicu(any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun searchAlbums_forQq_fallsBackToSingerAlbumListWhenDirectAlbumMissing() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val generalPayload = json.parseToJsonElement(
            """
            {
              "req": {
                "data": {
                  "body": {
                    "direct_result": {
                      "items": [
                        {
                          "id": "4558",
                          "restype": "singer",
                          "title": "周杰伦",
                          "custom_info": {
                            "mid": "0025NhlN2yWrP4"
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val albumsPayload = json.parseToJsonElement(
            """
            {
              "albums": {
                "data": {
                  "albumList": [
                    {
                      "albumID": 8220,
                      "albumMid": "000MkMni19ClKG",
                      "albumName": "叶惠美",
                      "publishDate": "2003-07-31",
                      "singerName": "周杰伦",
                      "totalNum": 11
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } returnsMany listOf(generalPayload, albumsPayload)

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchAlbums(Platform.QQ, "周杰伦", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("8220", data.first().id)
        assertEquals("叶惠美", data.first().title)
        assertEquals("周杰伦", data.first().subtitle)
        assertEquals("https://y.qq.com/music/photo_new/T002R300x300M000000MkMni19ClKG.jpg", data.first().coverUrl)
        assertEquals(11, data.first().trackCount)
        coVerify(exactly = 2) { api.postQqMusicu(any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun searchAlbums_forQq_prefersExactAlbumMatchesFromSongResultsOverDirectSingerFallback() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val generalPayload = json.parseToJsonElement(
            """
            {
              "req": {
                "data": {
                  "body": {
                    "direct_result": {
                      "items": [
                        {
                          "id": "4701",
                          "restype": "singer",
                          "title": "田馥甄",
                          "custom_info": {
                            "mid": "001ByAsv3XCdgm"
                          }
                        }
                      ]
                    },
                    "item_song": {
                      "items": [
                        {
                          "id": 341303751,
                          "mid": "003c0kQr1lRAgj",
                          "title": "无人知晓",
                          "album": {
                            "id": 14725932,
                            "mid": "003KCdYI3H1VPa",
                            "name": "无人知晓"
                          },
                          "singer": [
                            { "name": "田馥甄" }
                          ]
                        },
                        {
                          "id": 273771740,
                          "mid": "001blfaZ2vP1we",
                          "title": "无人知晓",
                          "album": {
                            "id": 14011044,
                            "mid": "000guZpI3dm0cf",
                            "name": "无人知晓"
                          },
                          "singer": [
                            { "name": "YU鱼" }
                          ]
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } returns generalPayload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchAlbums(Platform.QQ, "无人知晓", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(listOf("14725932", "14011044"), data.map { it.id })
        assertEquals(listOf("无人知晓", "无人知晓"), data.map { it.title })
        assertEquals(listOf("田馥甄", "YU鱼"), data.map { it.subtitle })
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000003KCdYI3H1VPa.jpg",
            data.first().coverUrl
        )
        coVerify(exactly = 1) { api.postQqMusicu(any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun searchPlaylists_forQq_usesTypedSearchWithMobileCtAndParsesArrayResults() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val requestBody = slot<JsonElement>()
        val payload = json.parseToJsonElement(
            """
            {
              "search": {
                "data": {
                  "body": {
                    "item_songlist": [
                      {
                        "dissid": "7039749142",
                        "dissname": "百听不厌的周杰伦",
                        "logo": "http://qpic.y.qq.com/music_cover/example/300?n=1",
                        "songnum": 99,
                        "nickname": "今晚月色很美"
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } answers {
            requestBody.captured = firstArg()
            payload
        }

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchPlaylists(Platform.QQ, "周杰伦", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("7039749142", data.first().id)
        assertEquals("百听不厌的周杰伦", data.first().title)
        assertEquals("今晚月色很美", data.first().subtitle)
        assertEquals("https://qpic.y.qq.com/music_cover/example/300?n=1", data.first().coverUrl)
        assertEquals(99, data.first().trackCount)

        val root = requestBody.captured as JsonObject
        val comm = root["comm"] as JsonObject
        val params = ((root["search"] as JsonObject)["param"]) as JsonObject
        assertEquals(11, (comm["ct"] as JsonPrimitive).content.toInt())
        assertEquals(3, (params["search_type"] as JsonPrimitive).content.toInt())
        assertEquals("周杰伦", (params["query"] as JsonPrimitive).content)
        coVerify(exactly = 1) { api.postQqMusicu(any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun searchPlaylists_forKuwo_usesOfficialApiInsteadOfDispatchTemplate() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": {
                "list": [
                  {
                    "id": "2891238463",
                    "name": "华语精选",
                    "img": "https://img4.kuwo.cn/star/albumcover/example.jpg",
                    "musicNum": 30,
                    "nickName": "酷我用户"
                  }
                ]
              }
            }
            """.trimIndent()
        )
        coEvery { api.searchKuwoPlaylists("华语", 1, 20, 1, any(), any()) } returns payload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.searchPlaylists(Platform.KUWO, "华语", 1, 20)

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("2891238463", data.first().id)
        assertEquals("华语精选", data.first().title)
        assertEquals("酷我用户", data.first().subtitle)
        assertEquals("https://img4.kuwo.cn/star/albumcover/example.jpg", data.first().coverUrl)
        assertEquals(30, data.first().trackCount)
        coVerify(exactly = 1) { api.searchKuwoPlaylists("华语", 1, 20, 1, any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), any(), any()) }
    }

    @Test
    fun extractNeteaseSearchResults_readsAlbumAndPlaylistStructure() {
        val payload = json.parseToJsonElement(
            """
            {
              "result": {
                "albums": [
                  {
                    "id": 1,
                    "name": "叶惠美",
                    "picUrl": "https://example.com/yhm.jpg",
                    "size": 11,
                    "artists": [
                      { "name": "周杰伦" }
                    ]
                  }
                ],
                "playlists": [
                  {
                    "id": 2,
                    "name": "周杰伦热门歌单",
                    "coverImgUrl": "https://example.com/playlist.jpg",
                    "trackCount": 50,
                    "creator": { "nickname": "测试用户" }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val albums = extractNeteaseSearchResults(payload, SearchType.ALBUM)
        val playlists = extractNeteaseSearchResults(payload, SearchType.PLAYLIST)

        assertEquals("叶惠美", albums.first().title)
        assertEquals("周杰伦", albums.first().subtitle)
        assertEquals(11, albums.first().trackCount)
        assertEquals("周杰伦热门歌单", playlists.first().title)
        assertEquals("测试用户", playlists.first().subtitle)
        assertEquals(50, playlists.first().trackCount)
    }

    @Test
    fun extractQqSearchResults_readsArtistAndPlaylistStructure() {
        val payload = json.parseToJsonElement(
            """
            {
              "search": {
                "data": {
                  "body": {
                    "singer": {
                      "list": [
                        {
                          "singerMID": "0025NhlN2yWrP4",
                          "singerName": "周杰伦",
                          "pic": "//y.gtimg.cn/music/photo_new/T001R300x300M0000025NhlN2yWrP4.jpg",
                          "songNum": 686
                        }
                      ]
                    },
                    "songlist": {
                      "list": [
                        {
                          "dissid": "9209322004",
                          "dissname": "周杰伦循环专区",
                          "imgurl": "http://p.qpic.cn/music_cover/example/600?n=1",
                          "song_count": 40,
                          "creatorName": "QQ用户"
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val artists = extractQqSearchResults(payload, SearchType.ARTIST)
        val playlists = extractQqSearchResults(payload, SearchType.PLAYLIST)

        assertEquals("0025NhlN2yWrP4", artists.first().id)
        assertEquals("周杰伦", artists.first().title)
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T001R300x300M0000025NhlN2yWrP4.jpg",
            artists.first().coverUrl
        )
        assertEquals(686, artists.first().trackCount)
        assertEquals("9209322004", playlists.first().id)
        assertEquals("周杰伦循环专区", playlists.first().title)
        assertEquals("QQ用户", playlists.first().subtitle)
        assertEquals(40, playlists.first().trackCount)
    }

    @Test
    fun extractKuwoSearchResults_readsArtistAndAlbumStructure() {
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "data": {
                "artistList": [
                  {
                    "id": "123",
                    "name": "周杰伦",
                    "pic": "https://img4.kuwo.cn/star/artistcover/jay.jpg",
                    "musicNum": 686
                  }
                ],
                "albumList": [
                  {
                    "albumid": "456",
                    "album": "范特西",
                    "artist": "周杰伦",
                    "pic": "starheads/202/32/12/example.jpg",
                    "songnum": 10
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val artists = extractKuwoSearchResults(payload, SearchType.ARTIST)
        val albums = extractKuwoSearchResults(payload, SearchType.ALBUM)

        assertEquals("123", artists.first().id)
        assertEquals("周杰伦", artists.first().title)
        assertEquals(686, artists.first().trackCount)
        assertEquals("456", albums.first().id)
        assertEquals("范特西", albums.first().title)
        assertEquals("周杰伦", albums.first().subtitle)
        assertEquals("https://img4.kuwo.cn/star/albumcover/starheads/202/32/12/example.jpg", albums.first().coverUrl)
        assertEquals(10, albums.first().trackCount)
    }

    @Test
    fun getAlbumDetail_forNetease_usesOfficialApiInsteadOfDispatchTemplate() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "code": 200,
              "songs": [
                {
                  "id": 185811,
                  "name": "晴天",
                  "dt": 269000,
                  "artists": [
                    { "name": "周杰伦" }
                  ],
                  "album": {
                    "id": 32311,
                    "name": "叶惠美",
                    "picUrl": "https://example.com/yhm.jpg"
                  }
                }
              ]
            }
            """.trimIndent()
        )
        coEvery { api.getNeteaseAlbumDetail("32311", any()) } returns payload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getAlbumDetail(Platform.NETEASE, "32311")

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("185811", data.first().id)
        assertEquals("晴天", data.first().title)
        assertEquals("周杰伦", data.first().artist)
        assertEquals("叶惠美", data.first().album)
        assertEquals("32311", data.first().albumId)
        coVerify(exactly = 1) { api.getNeteaseAlbumDetail("32311", any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), eq("albumDetail"), any()) }
    }

    @Test
    fun getAlbumDetail_forQq_usesOfficialApiInsteadOfDispatchTemplate() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val requestBody = slot<JsonElement>()
        val payload = json.parseToJsonElement(
            """
            {
              "songs": {
                "code": 0,
                "data": {
                  "totalNum": 1,
                  "songList": [
                    {
                      "songInfo": {
                        "mid": "0039MnYb0qxYhV",
                        "title": "晴天",
                        "interval": 269,
                        "singer": [
                          { "name": "周杰伦" }
                        ],
                        "album": {
                          "id": 8220,
                          "mid": "000MkMni19ClKG",
                          "name": "叶惠美"
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } answers {
            requestBody.captured = firstArg()
            payload
        }

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getAlbumDetail(Platform.QQ, "8220")

        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("0039MnYb0qxYhV", data.first().id)
        assertEquals("晴天", data.first().title)
        assertEquals("周杰伦", data.first().artist)
        assertEquals("叶惠美", data.first().album)
        assertEquals("8220", data.first().albumId)
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000000MkMni19ClKG.jpg",
            data.first().coverUrl
        )
        assertEquals(269000L, data.first().durationMs)

        val root = requestBody.captured as JsonObject
        val songs = root["songs"] as JsonObject
        val params = songs["param"] as JsonObject
        assertEquals("music.musichallAlbum.AlbumSongList", (songs["module"] as JsonPrimitive).content)
        assertEquals("GetAlbumSongList", (songs["method"] as JsonPrimitive).content)
        assertEquals(8220L, (params["albumId"] as JsonPrimitive).content.toLong())
        assertEquals(300, (params["num"] as JsonPrimitive).content.toInt())
        coVerify(exactly = 1) { api.postQqMusicu(any(), any()) }
        coVerify(exactly = 0) { dispatchExecutor.executeByMethod(any(), eq("albumDetail"), any()) }
    }

    @Test
    fun getArtistDetail_forQq_usesSingerSongAndAlbumEndpoints() = runTest {
        val api = mockk<TuneHubApi>()
        val dispatchExecutor = mockk<DispatchExecutor>(relaxed = true)
        val cacheStore = mockk<HomeContentCacheStore>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val payload = json.parseToJsonElement(
            """
            {
              "songs": {
                "data": {
                  "songList": [
                    {
                      "songInfo": {
                        "mid": "0039MnYb0qxYhV",
                        "title": "晴天",
                        "interval": 269,
                        "singer": [
                          { "name": "周杰伦", "mid": "0025NhlN2yWrP4" }
                        ],
                        "album": {
                          "name": "叶惠美",
                          "mid": "000MkMni19ClKG"
                        }
                      }
                    }
                  ]
                }
              },
              "albums": {
                "data": {
                  "albumList": [
                    {
                      "albumID": 8220,
                      "albumMid": "000MkMni19ClKG",
                      "albumName": "叶惠美",
                      "publishDate": "2003-07-31",
                      "singerName": "周杰伦",
                      "totalNum": 11
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        coEvery { api.postQqMusicu(any(), any()) } returns payload

        val repository = OnlineMusicRepositoryImpl(api, okHttpClient, dispatchExecutor, json, cacheStore)

        val result = repository.getArtistDetail("0025NhlN2yWrP4", Platform.QQ)

        val data = (result as Result.Success).data
        assertEquals("周杰伦", data.name)
        assertEquals("https://y.qq.com/music/photo_new/T001R300x300M0000025NhlN2yWrP4.jpg", data.avatarUrl)
        assertEquals(1, data.hotSongs.size)
        assertEquals("晴天", data.hotSongs.first().title)
        assertEquals(1, data.albums.size)
        assertEquals("叶惠美", data.albums.first().name)
        coVerify(exactly = 1) { api.postQqMusicu(any(), any()) }
    }
}
