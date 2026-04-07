package com.music.myapplication.data.repository.lx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LxCustomScriptRuntimeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun parseLxResponseBody_parsesJsonObject() {
        val result = parseLxResponseBody(
            """{"code":0,"data":"https://example.com/play.mp3"}""",
            json
        )

        assertTrue(result is JsonObject)
        val body = result as JsonObject
        assertEquals("0", body["code"]?.toString()?.trim('"'))
        assertEquals("https://example.com/play.mp3", body["data"]?.toString()?.trim('"'))
    }

    @Test
    fun parseLxResponseBody_keepsPlainTextAsString() {
        val result = parseLxResponseBody("plain-text-body", json)

        assertTrue(result is JsonPrimitive)
        assertEquals("plain-text-body", (result as JsonPrimitive).content)
    }

    @Test
    fun toSupportedLxQualities_filtersUnknownEntries() {
        val result = listOf("128k", "hires", "320k", "master", "flac24bit").toSupportedLxQualities()

        assertEquals(listOf("128k", "320k", "flac24bit"), result)
    }

    @Test
    fun toSupportedLxQualities_trimsAndDeduplicatesValues() {
        val result = listOf(" 128k ", "320k", "128k", " ", "flac").toSupportedLxQualities()

        assertEquals(listOf("128k", "320k", "flac"), result)
    }

    @Test
    fun lxPreludeCompatibilitySnippet_containsLegacyGlobalAliases() {
        val snippet = lxPreludeCompatibilitySnippet()

        assertTrue(snippet.contains("globalThis.window = globalThis;"))
        assertTrue(snippet.contains("globalThis.self = globalThis;"))
        assertTrue(snippet.contains("globalThis.global = globalThis;"))
        assertTrue(snippet.contains("globalThis.event_names = EVENT_NAMES;"))
    }

    @Test
    fun lxMusicPayload_serializationContainsCompatibilityAliases() {
        val payload = LxMusicRequestPayload(
            type = "320k",
            musicInfo = LxMusicInfo(
                id = "123",
                songmid = "123",
                mid = "123",
                name = "晴天",
                title = "晴天",
                artist = "周杰伦",
                singer = "周杰伦",
                album = "叶惠美",
                albumName = "叶惠美",
                albumId = "456",
                pic = "https://example.com/cover.jpg",
                picUrl = "https://example.com/cover.jpg",
                interval = 258,
                duration = 258,
                source = "wy",
                quality = "320k"
            )
        )

        val encoded = json.encodeToString(payload)

        assertTrue(encoded.contains("\"quality\":\"320k\""))
        assertTrue(encoded.contains("\"music\""))
        assertTrue(encoded.contains("\"songInfo\""))
        assertTrue(encoded.contains("\"songId\":\"123\""))
        assertTrue(encoded.contains("\"coverUrl\":\"https://example.com/cover.jpg\""))
        assertTrue(encoded.contains("\"sourceId\":\"wy\""))
    }

    @Test
    fun lxPreludeCompatibilitySnippet_keepsLegacyEventAliasesOnly() {
        val snippet = lxPreludeCompatibilitySnippet()

        assertTrue(snippet.contains("globalThis.EVENT_NAMES = EVENT_NAMES;"))
        assertTrue(snippet.contains("globalThis.event_names = EVENT_NAMES;"))
    }

    @Test
    fun preludeContainsBufferCompatibilityLayer() {
        val snippet = lxBufferCompatibilitySnippet()

        assertTrue(snippet.contains("class Buffer extends Uint8Array"))
        assertTrue(snippet.contains("globalThis.Buffer = Buffer;"))
        assertTrue(snippet.contains("__lxHostBufferFrom(value, String(encoding))"))
    }
}
