package com.music.myapplication.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMusicRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
