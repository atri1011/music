package com.music.myapplication.core.network.dispatch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateRendererTest {

    private val renderer = TemplateRenderer()

    @Test
    fun renderBody_supportsParseIntExpression() {
        val template = Json.parseToJsonElement(
            """{"toplist":{"param":{"topid":"{{parseInt(id)}}"}}}"""
        )

        val rendered = renderer.renderBody(template, mapOf("id" to "62"))
        val topId = Json.parseToJsonElement(rendered!!)
            .jsonObject["toplist"]!!
            .jsonObject["param"]!!
            .jsonObject["topid"]!!
            .jsonPrimitive
            .content

        assertEquals("62", topId)
    }
}
