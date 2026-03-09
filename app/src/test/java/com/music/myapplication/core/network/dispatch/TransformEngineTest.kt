package com.music.myapplication.core.network.dispatch

import com.music.myapplication.domain.model.Platform
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformEngineTest {

    private val engine = TransformEngine(Json { ignoreUnknownKeys = true })

    @Test
    fun transform_prefersSourceIdForKuwoToplistItems() {
        val response = """
            {
              "child": [
                {
                  "id": "534804",
                  "sourceid": "340",
                  "name": "青少年专属飙升榜",
                  "source": "1",
                  "pic": "http://img2.kwcdn.kuwo.cn/star/upload/8/8/1543919795640_.png"
                }
              ]
            }
        """.trimIndent()

        val tracks = engine.transform(response, rule = null, platform = Platform.KUWO)

        assertTrue(tracks.isNotEmpty())
        assertEquals("340", tracks.first().id)
    }

    @Test
    fun transform_keepsSongIdWhenArtistExists() {
        val response = """
            {
              "musiclist": [
                {
                  "id": "530900521",
                  "sourceid": "340",
                  "name": "不会太晚",
                  "artist": "刘雨昕",
                  "album": "不会太晚"
                }
              ]
            }
        """.trimIndent()

        val tracks = engine.transform(response, rule = null, platform = Platform.KUWO)

        assertTrue(tracks.isNotEmpty())
        assertEquals("530900521", tracks.first().id)
    }

    @Test
    fun transform_prefersQqMidWhenArtistExists() {
        val response = """
            {
              "toplist": {
                "data": {
                  "songInfoList": [
                    {
                      "id": 645177322,
                      "mid": "002Kgaz04dXb1Z",
                      "title": "痴人说梦",
                      "singerList": [
                        { "name": "HOYO-MiX" }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val tracks = engine.transform(response, rule = null, platform = Platform.QQ)

        assertTrue(tracks.isNotEmpty())
        assertEquals("002Kgaz04dXb1Z", tracks.first().id)
    }

    @Test
    fun transform_withRule_stillPrefersQqMid() {
        val response = """
            {
              "toplist": {
                "data": {
                  "songInfoList": [
                    {
                      "id": 641952544,
                      "mid": "0034T1fQ2xY9yC",
                      "title": "测试歌曲",
                      "singerList": [{ "name": "测试歌手" }]
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val rule = Json.parseToJsonElement(
            """
            {
              "root": "toplist.data.songInfoList",
              "fields": {
                "id": "id",
                "title": "title",
                "artist": "singerList"
              }
            }
            """.trimIndent()
        )

        val tracks = engine.transform(responseBody = response, rule = rule, platform = Platform.QQ)

        assertTrue(tracks.isNotEmpty())
        assertEquals("0034T1fQ2xY9yC", tracks.first().id)
    }

    @Test
    fun transform_withRule_mapsAlbumIdFromKnownAliases() {
        val response = """
            {
              "result": [
                {
                  "id": "185811",
                  "name": "晴天",
                  "artist": "周杰伦",
                  "album": {
                    "id": "32311",
                    "name": "叶惠美"
                  }
                }
              ]
            }
        """.trimIndent()
        val rule = Json.parseToJsonElement(
            """
            {
              "root": "result",
              "fields": {
                "id": "id",
                "title": "name",
                "artist": "artist",
                "album": "album.name"
              }
            }
            """.trimIndent()
        )

        val tracks = engine.transform(responseBody = response, rule = rule, platform = Platform.NETEASE)

        assertTrue(tracks.isNotEmpty())
        assertEquals("32311", tracks.first().albumId)
    }
}
