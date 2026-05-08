package com.music.myapplication.data.repository.recipe

import com.music.myapplication.core.network.dispatch.TemplateRenderer
import com.music.myapplication.domain.model.PlayableUrlRecipe
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRequestBuilder @Inject constructor(
    private val templateRenderer: TemplateRenderer
) {
    fun buildRequest(
        recipe: PlayableUrlRecipe,
        track: Track,
        quality: String,
        authValues: Map<String, String> = emptyMap()
    ): Request {
        val templateParams = buildTemplateParams(recipe, track, quality, authValues)

        val renderedUrl = templateRenderer.renderUrl(
            urlTemplate = recipe.request.url,
            templateParams = templateParams
        )

        val requestBuilder = Request.Builder().url(renderedUrl)

        val renderedHeaders = recipe.request.headers.mapValues { (_, value) ->
            templateRenderer.renderUrl(value, templateParams)
        }
        if (renderedHeaders.isNotEmpty()) {
            requestBuilder.headers(renderedHeaders.toHeaders())
        }

        val method = recipe.request.method.uppercase()
        when {
            method == "GET" || method == "HEAD" -> requestBuilder.method(method, null)
            recipe.request.body != null -> {
                val renderedBody = templateRenderer.renderBody(recipe.request.body, templateParams)
                val contentType = "application/json; charset=utf-8".toMediaType()
                requestBuilder.method(method, renderedBody?.toRequestBody(contentType))
            }
            else -> requestBuilder.method(method, "".toRequestBody())
        }

        return requestBuilder.build()
    }

    private fun buildTemplateParams(
        recipe: PlayableUrlRecipe,
        track: Track,
        quality: String,
        authValues: Map<String, String>
    ): Map<String, String> {
        val params = mutableMapOf<String, String>()

        params["title"] = track.title
        params["artist"] = track.artist
        params["album"] = track.album
        params["trackId"] = track.id
        params["platform"] = track.platform.id
        params["quality"] = quality

        recipe.qualityMap[quality]?.let { mapped ->
            params["qualityMapped"] = mapped
        }

        recipe.platformVars[track.platform.name]?.forEach { (key, value) ->
            params["platformVars.$key"] = value
        }

        authValues.forEach { (key, value) ->
            params["auth.$key"] = value
        }

        return params
    }
}
