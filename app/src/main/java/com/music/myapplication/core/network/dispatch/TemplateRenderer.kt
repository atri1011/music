package com.music.myapplication.core.network.dispatch

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRenderer @Inject constructor() {
    private val placeholderRegex = Regex("""\{\{\s*([^{}]+?)\s*\}\}""")

    fun renderUrl(
        urlTemplate: String,
        templateParams: Map<String, String>,
        queryParams: Map<String, String> = emptyMap()
    ): String {
        val renderedBase = renderString(urlTemplate, templateParams)
        if (queryParams.isEmpty()) return renderedBase

        val builder = renderedBase.toHttpUrlOrNull()?.newBuilder() ?: return renderedBase
        queryParams.forEach { (key, valueTemplate) ->
            builder.addQueryParameter(key, renderString(valueTemplate, templateParams))
        }
        return builder.build().toString()
    }

    fun renderBody(bodyTemplate: JsonElement?, params: Map<String, String>): String? {
        if (bodyTemplate == null) return null
        val rendered = renderJsonElement(bodyTemplate, params)
        return (rendered as? JsonPrimitive)?.contentOrNull ?: rendered.toString()
    }

    private fun renderString(template: String, params: Map<String, String>): String {
        return placeholderRegex.replace(template) { match ->
            val expression = match.groupValues[1]
            resolveExpression(expression, params) ?: ""
        }
    }

    private fun resolveExpression(expression: String, params: Map<String, String>): String? {
        val candidates = expression
            .split("||")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (candidate in candidates) {
            val value = resolveToken(candidate, params)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun resolveToken(token: String, params: Map<String, String>): String? {
        params[token]?.let { return it }
        val unquoted = token.removeSurrounding("'").removeSurrounding("\"")
        if (unquoted != token) return unquoted
        if (token == "null" || token == "undefined") return null
        if (token.matches(Regex("""-?\d+(\.\d+)?"""))) return token
        if (token == "true" || token == "false") return token
        return params[token.removePrefix("$")]
    }

    private fun renderJsonElement(element: JsonElement, params: Map<String, String>): JsonElement {
        return when (element) {
            is JsonObject -> JsonObject(
                element.mapValues { (_, value) -> renderJsonElement(value, params) }
            )
            is JsonArray -> JsonArray(element.map { value -> renderJsonElement(value, params) })
            is JsonPrimitive -> {
                if (element.isString) {
                    val template = element.content
                    val rendered = renderString(template, params)
                    if (placeholderRegex.matches(template.trim())) {
                        rendered.toLongOrNull()?.let { return JsonPrimitive(it) }
                        rendered.toDoubleOrNull()?.let { return JsonPrimitive(it) }
                        rendered.toBooleanStrictOrNull()?.let { return JsonPrimitive(it) }
                    }
                    JsonPrimitive(rendered)
                } else {
                    element
                }
            }
        }
    }
}
