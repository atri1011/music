package com.music.myapplication.data.repository.recipe

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.PlayableUrlRecipe
import com.music.myapplication.domain.model.RecipeSuccessCondition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeResponseExtractor @Inject constructor() {

    fun extract(
        recipe: PlayableUrlRecipe,
        response: Response,
        responseBody: String?
    ): Result<String> {
        return when (recipe.extract.kind) {
            "redirect_location" -> extractFromRedirect(response, recipe)
            "json_path" -> extractFromJsonPath(responseBody ?: "", recipe)
            else -> Result.Error(AppError.Parse(message = "不支持的提取方式: ${recipe.extract.kind}"))
        }
    }

    private fun extractFromRedirect(response: Response, recipe: PlayableUrlRecipe): Result<String> {
        val location = response.header("Location")
        if (location.isNullOrBlank()) {
            val code = response.code
            return if (code == 429) {
                Result.Error(AppError.Api(message = "请求过于频繁，请稍后重试", code = 429))
            } else {
                Result.Error(AppError.Api(message = "未返回重定向链接（HTTP $code）", code = code))
            }
        }
        if (!location.startsWith("http", ignoreCase = true)) {
            return Result.Error(AppError.Parse(message = "重定向链接无效: $location"))
        }
        return Result.Success(location)
    }

    private fun extractFromJsonPath(body: String, recipe: PlayableUrlRecipe): Result<String> {
        val jsonElement = try {
            kotlinx.serialization.json.Json.parseToJsonElement(body)
        } catch (e: Exception) {
            return Result.Error(AppError.Parse(message = "响应不是有效的 JSON: ${e.message}"))
        }

        recipe.extract.successCondition?.let { condition ->
            val checkResult = checkSuccessCondition(jsonElement, condition, recipe)
            if (checkResult != null) return checkResult
        }

        val path = recipe.extract.playableUrlPath ?: return Result.Error(
            AppError.Parse(message = "Recipe 缺少 playableUrlPath 配置")
        )

        val urlElement = resolveJsonPath(jsonElement, path)
        val url = (urlElement as? JsonPrimitive)?.contentOrNull
            ?: urlElement?.toString()?.removeSurrounding("\"")

        if (url.isNullOrBlank()) {
            return Result.Error(AppError.Parse(message = "未找到播放地址 (path: $path)"))
        }
        if (!url.startsWith("http", ignoreCase = true)) {
            return Result.Error(AppError.Parse(message = "播放地址无效: $url"))
        }
        return Result.Success(url)
    }

    private fun checkSuccessCondition(
        jsonElement: JsonElement,
        condition: RecipeSuccessCondition,
        recipe: PlayableUrlRecipe
    ): Result<Nothing>? {
        val targetElement = resolveJsonPath(jsonElement, condition.path)
        val targetValue = (targetElement as? JsonPrimitive)?.contentOrNull
            ?: targetElement?.toString()?.removeSurrounding("\"")

        condition.equals?.let { expected ->
            val expectedStr = (expected as? JsonPrimitive)?.contentOrNull ?: expected.toString()
            if (targetValue != expectedStr) {
                val errorMsg = extractErrorMessage(jsonElement, recipe.extract.errorMessagePath)
                return Result.Error(
                    AppError.Api(message = errorMsg ?: "请求失败 (path=${condition.path}, got=$targetValue, expected=$expectedStr)")
                )
            }
        }

        condition.notEquals?.let { unexpected ->
            val unexpectedStr = (unexpected as? JsonPrimitive)?.contentOrNull ?: unexpected.toString()
            if (targetValue == unexpectedStr) {
                val errorMsg = extractErrorMessage(jsonElement, recipe.extract.errorMessagePath)
                return Result.Error(
                    AppError.Api(message = errorMsg ?: "请求失败 (path=${condition.path}, value=$targetValue)")
                )
            }
        }

        return null
    }

    private fun extractErrorMessage(jsonElement: JsonElement, errorMessagePath: String?): String? {
        if (errorMessagePath.isNullOrBlank()) return null
        val errorElement = resolveJsonPath(jsonElement, errorMessagePath)
        return (errorElement as? JsonPrimitive)?.contentOrNull
    }

    fun resolveJsonPath(element: JsonElement, path: String): JsonElement? {
        val segments = path.split(".")
        var current: JsonElement? = element
        for (segment in segments) {
            current = when (current) {
                is JsonObject -> {
                    current[segment] ?: current.entries.firstOrNull {
                        it.key.equals(segment, ignoreCase = true)
                    }?.value
                }
                is JsonArray -> {
                    val index = segment.toIntOrNull()
                    if (index != null && index in current.indices) current[index] else null
                }
                else -> null
            }
            if (current == null) return null
        }
        return current
    }
}
