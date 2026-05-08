package com.music.myapplication.data.repository.recipe

import com.music.myapplication.domain.model.PlayableUrlRecipe
import com.music.myapplication.domain.model.RecipeAuthField
import com.music.myapplication.domain.model.RecipeExtract
import com.music.myapplication.domain.model.RecipeRequest
import javax.inject.Inject
import javax.inject.Singleton

data class RecipeValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    fun requireValid() {
        if (!isValid) throw IllegalArgumentException(errors.joinToString("; "))
    }
}

@Singleton
class RecipeValidator @Inject constructor() {

    fun validate(recipe: PlayableUrlRecipe): RecipeValidationResult {
        val errors = mutableListOf<String>()

        if (recipe.id.isBlank()) {
            errors.add("id is required")
        }
        if (recipe.id.isNotBlank() && !RECIPE_ID_REGEX.matches(recipe.id)) {
            errors.add("id must contain only lowercase letters, digits, underscores, and hyphens")
        }
        if (recipe.displayName.isBlank()) {
            errors.add("displayName is required")
        }
        if (recipe.schemaVersion != 1) {
            errors.add("schemaVersion must be 1")
        }

        validateRequest(recipe.request, errors)
        validateExtract(recipe.extract, errors)
        validateAuth(recipe.auth, errors)
        validatePlatformVars(recipe, errors)

        if (recipe.request.url.isNotBlank()) {
            val url = recipe.request.url
            val hasHttpScheme = url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)
            val isTemplate = url.contains("{{")
            if (!hasHttpScheme && !isTemplate) {
                errors.add("request.url must start with https:// or http://")
            }
            if (hasHttpScheme && url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("http://localhost", ignoreCase = true) &&
                !url.startsWith("http://127.0.0.1", ignoreCase = true) &&
                !url.startsWith("http://10.", ignoreCase = true) &&
                !url.startsWith("http://192.168.", ignoreCase = true) &&
                !url.startsWith("http://172.", ignoreCase = true)
            ) {
                errors.add("request.url should use https:// instead of http://")
            }
        }

        return RecipeValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    fun validateJson(jsonText: String): RecipeValidationResult {
        val errors = mutableListOf<String>()
        val recipe = try {
            kotlinx.serialization.json.Json.decodeFromString<PlayableUrlRecipe>(jsonText)
        } catch (e: Exception) {
            return RecipeValidationResult(
                isValid = false,
                errors = listOf("JSON 解析失败: ${e.message}")
            )
        }
        val structResult = validate(recipe)
        return RecipeValidationResult(
            isValid = structResult.isValid,
            errors = structResult.errors
        )
    }

    private fun validateRequest(request: RecipeRequest, errors: MutableList<String>) {
        val validMethods = setOf("GET", "POST", "PUT", "HEAD")
        if (request.method.uppercase() !in validMethods) {
            errors.add("request.method must be one of: ${validMethods.joinToString(", ")}")
        }
        if (request.url.isBlank()) {
            errors.add("request.url is required")
        }
        if (request.timeoutSeconds < 1 || request.timeoutSeconds > 60) {
            errors.add("request.timeoutSeconds must be between 1 and 60")
        }
        val unclosedBraces = countUnclosed(request.url)
        if (unclosedBraces > 0) {
            errors.add("request.url has $unclosedBraces unclosed template placeholder(s)")
        }
        request.headers.values.forEach { headerValue ->
            val hUnclosed = countUnclosed(headerValue)
            if (hUnclosed > 0) {
                errors.add("request.headers value has $hUnclosed unclosed template placeholder(s)")
            }
        }
    }

    private fun validateExtract(extract: RecipeExtract, errors: MutableList<String>) {
        val validKinds = setOf("json_path", "redirect_location")
        if (extract.kind !in validKinds) {
            errors.add("extract.kind must be one of: ${validKinds.joinToString(", ")}")
        }
        if (extract.kind == "json_path" && extract.playableUrlPath.isNullOrBlank()) {
            errors.add("extract.playableUrlPath is required when extract.kind is 'json_path'")
        }
        if (extract.kind == "redirect_location" && extract.playableUrlPath != null) {
            errors.add("extract.playableUrlPath should not be set when extract.kind is 'redirect_location'")
        }
    }

    private fun validateAuth(auth: List<RecipeAuthField>, errors: MutableList<String>) {
        val keys = mutableSetOf<String>()
        for (field in auth) {
            if (field.key.isBlank()) {
                errors.add("auth field key must not be blank")
            }
            if (!keys.add(field.key)) {
                errors.add("auth field key '${field.key}' is duplicated")
            }
            val validTypes = setOf("text", "password")
            if (field.type !in validTypes) {
                errors.add("auth field '${field.key}' type must be one of: ${validTypes.joinToString(", ")}")
            }
        }
    }

    private fun validatePlatformVars(recipe: PlayableUrlRecipe, errors: MutableList<String>) {
        for ((platformKey, vars) in recipe.platformVars) {
            if (vars.isEmpty()) {
                errors.add("platformVars['$platformKey'] must not be empty")
            }
        }
    }

    private fun countUnclosed(template: String): Int {
        var count = 0
        var i = 0
        while (i < template.length - 1) {
            if (template[i] == '{' && template[i + 1] == '{') {
                val closeIdx = template.indexOf("}}", i + 2)
                if (closeIdx == -1) {
                    count++
                }
                i = if (closeIdx != -1) closeIdx + 2 else template.length
            } else {
                i++
            }
        }
        return count
    }

    companion object {
        private val RECIPE_ID_REGEX = Regex("^[a-z0-9_-]+$")
    }
}
