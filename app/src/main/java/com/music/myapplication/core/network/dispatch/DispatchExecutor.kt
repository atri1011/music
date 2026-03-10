package com.music.myapplication.core.network.dispatch

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.DispatchersProvider
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.cache.CacheManager
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class DispatchExecutor @Inject constructor(
    private val api: TuneHubApi,
    private val httpClient: OkHttpClient,
    private val templateCache: DispatchTemplateCache,
    private val cacheManager: CacheManager,
    private val renderer: TemplateRenderer,
    private val validator: RequestValidator,
    private val transformEngine: TransformEngine,
    private val dispatchersProvider: DispatchersProvider
) {

    suspend fun executeByMethod(
        platform: Platform,
        function: String,
        args: Map<String, String>
    ): Result<List<Track>> = withContext(dispatchersProvider.io) {
        try {
            val template = when (val templateResult = getTemplate(platform, function)) {
                is Result.Success -> templateResult.data
                is Result.Error -> return@withContext Result.Error(templateResult.error)
                is Result.Loading -> return@withContext Result.Error(
                    AppError.Template("Template loading state is unexpected for ${platform.id}/$function")
                )
            }

            val data = template.data
            val renderedUrl = renderer.renderUrl(data.url, args, data.params)

            if (!validator.validate(renderedUrl)) {
                return@withContext Result.Error(AppError.Template("URL validation failed: $renderedUrl"))
            }

            val renderedBody = renderer.renderBody(data.body, args)
            val response = executeRequest(renderedUrl, data.method, data.headers, renderedBody)
            val tracks = transformEngine.transform(response, data.transform, platform)
            val resolvedTracks = tryQqSearchFallbackIfNeeded(
                platform = platform,
                function = function,
                args = args,
                transformRule = data.transform,
                currentTracks = tracks
            )
            Result.Success(resolvedTracks)
        } catch (e: IOException) {
            Result.Error(AppError.Network(cause = e))
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(message = e.message ?: "Unknown error", cause = e))
        }
    }

    private suspend fun getTemplate(platform: Platform, function: String): Result<DispatchTemplate> {
        templateCache.get(platform.id, function)?.let { return Result.Success(it) }
        return try {
            val response = api.getMethodTemplate(platform.id, function)
            if (response.code != 0 || response.data == null) {
                return Result.Error(
                    AppError.Template(
                        message = "Template API error for ${platform.id}/$function: code=${response.code}, msg=${response.msg}"
                    )
                )
            }
            val template = DispatchTemplate(
                platform = platform.id,
                function = function,
                data = response.data
            )
            templateCache.put(platform.id, function, template)
            cacheManager.enforceLimit()
            Result.Success(template)
        } catch (e: Exception) {
            Result.Error(
                AppError.Template(
                    message = "Failed to fetch template for ${platform.id}/$function: ${e.message ?: e.javaClass.simpleName}",
                    cause = e
                )
            )
        }
    }

    private suspend fun executeRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): String {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toRequestBody())
            "GET" -> requestBuilder.get()
        }
        return httpClient.newCall(requestBuilder.build()).await()
    }

    private suspend fun Call.await(): String = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        if (!cont.isCancelled) {
                            cont.resumeWithException(IOException("HTTP ${res.code}: ${res.message}"))
                        }
                        return
                    }
                    if (!cont.isCancelled) {
                        cont.resume(res.body?.string() ?: "")
                    }
                }
            }
        })
    }

    private suspend fun tryQqSearchFallbackIfNeeded(
        platform: Platform,
        function: String,
        args: Map<String, String>,
        transformRule: JsonElement?,
        currentTracks: List<Track>
    ): List<Track> {
        if (platform != Platform.QQ || function != "search" || currentTracks.isNotEmpty()) {
            return currentTracks
        }

        val keyword = args["keyword"]?.trim().orEmpty()
        if (keyword.isBlank()) return currentTracks

        val pageNum = args["page_num"] ?: args["page"] ?: "1"
        val pageSize = args["num_per_page"] ?: args["pageSize"] ?: "20"

        return runCatching {
            val fallbackBody = buildQqSearchFallbackBody(keyword, pageNum, pageSize)
            val fallbackResponse = executeRequest(
                url = QQ_SEARCH_FALLBACK_URL,
                method = "POST",
                headers = QQ_SEARCH_FALLBACK_HEADERS,
                body = fallbackBody
            )
            val fallbackTracks = transformEngine.transform(fallbackResponse, transformRule, platform)
            if (fallbackTracks.isNotEmpty()) fallbackTracks else currentTracks
        }.getOrDefault(currentTracks)
    }

    private fun buildQqSearchFallbackBody(keyword: String, pageNumRaw: String, pageSizeRaw: String): String {
        val pageNum = pageNumRaw.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val pageSize = pageSizeRaw.toIntOrNull()?.coerceIn(1, 50) ?: 20
        return buildJsonObject {
            put(
                "comm",
                buildJsonObject {
                    put("cv", 4747474)
                    put("ct", 24)
                    put("format", "json")
                    put("inCharset", "utf-8")
                    put("outCharset", "utf-8")
                    put("uin", 0)
                }
            )
            put(
                "req",
                buildJsonObject {
                    put("method", "do_search_v2")
                    put("module", "music.adaptor.SearchAdaptor")
                    put(
                        "param",
                        buildJsonObject {
                            put("query", keyword)
                            put("search_type", 100)
                            put("page_num", pageNum)
                            put("num_per_page", pageSize)
                            put("highlight", true)
                            put("grp", 1)
                        }
                    )
                }
            )
        }.toString()
    }

    private companion object {
        private const val QQ_SEARCH_FALLBACK_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private val QQ_SEARCH_FALLBACK_HEADERS = mapOf(
            "Content-Type" to "application/json",
            "Referer" to "https://y.qq.com/",
            "User-Agent" to "MusicPlayer/1.0 Android"
        )
    }
}
