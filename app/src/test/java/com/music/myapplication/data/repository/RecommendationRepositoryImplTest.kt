package com.music.myapplication.data.repository

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
}
