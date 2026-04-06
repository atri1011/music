package com.music.myapplication.data.repository.lx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
}
