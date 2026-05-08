package com.music.myapplication.data.repository.recipe

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlayableUrlRecipe
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipePlayableResolver @Inject constructor(
    private val requestBuilder: RecipeRequestBuilder,
    private val responseExtractor: RecipeResponseExtractor,
    private val preferences: PlayerPreferences,
    okHttpClient: OkHttpClient
) {
    private val noRedirectClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val defaultClient = okHttpClient

    suspend fun resolve(
        track: Track,
        quality: String,
        recipe: PlayableUrlRecipe
    ): Result<String> = withContext(Dispatchers.IO) {
        if (track.platform == Platform.LOCAL) {
            return@withContext Result.Error(AppError.Parse(message = "本地歌曲无需解析"))
        }

        val platformName = track.platform.name
        if (recipe.supportedPlatforms.isNotEmpty() &&
            !recipe.supportedPlatforms.any { it.equals(platformName, ignoreCase = true) }
        ) {
            return@withContext Result.Error(
                AppError.Api(message = "${recipe.displayName} 不支持${track.platform.displayName}")
            )
        }

        val authValues = collectAuthValues(recipe)

        val missingRequired = recipe.auth.filter { it.required }
            .filter { field -> authValues[field.key].isNullOrBlank() }
        if (missingRequired.isNotEmpty()) {
            val labels = missingRequired.joinToString("、") { it.label }
            return@withContext Result.Error(
                AppError.Api(message = "请先配置${recipe.displayName}的必填项：$labels")
            )
        }

        val client = if (!recipe.request.followRedirects) noRedirectClient else defaultClient
        val timeoutSeconds = recipe.request.timeoutSeconds.coerceIn(1, 60)

        val request = try {
            requestBuilder.buildRequest(recipe, track, quality, authValues)
        } catch (e: Exception) {
            return@withContext Result.Error(AppError.Parse(message = "构建请求失败: ${e.message}"))
        }

        return@withContext try {
            client.newBuilder()
                .connectTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute().use { response ->
                    val body = if (recipe.extract.kind != "redirect_location") {
                        response.body?.string()
                    } else {
                        null
                    }
                    responseExtractor.extract(recipe, response, body)
                }
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun collectAuthValues(recipe: PlayableUrlRecipe): Map<String, String> {
        val values = mutableMapOf<String, String>()
        if (recipe.id == "jkapi") {
            preferences.jkapiKey.firstOrNull()?.let { if (it.isNotBlank()) values["apiKey"] = it }
        }
        if (recipe.id == "netease_cloud_api_enhanced") {
            preferences.neteaseCloudApiBaseUrl.firstOrNull()?.let {
                if (it.isNotBlank()) values["baseUrl"] = it
            }
        }
        return values
    }

    fun isReasonableMatch(track: Track, responseName: String?): Boolean {
        if (responseName.isNullOrBlank()) return true
        return track.title.contains(responseName, ignoreCase = true) ||
            responseName.contains(track.title, ignoreCase = true)
    }
}
