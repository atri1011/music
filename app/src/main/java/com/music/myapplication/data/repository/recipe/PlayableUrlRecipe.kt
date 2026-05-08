package com.music.myapplication.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayableUrlRecipe(
    val schemaVersion: Int = 1,
    val id: String,
    val displayName: String,
    val version: String = "1.0.0",
    val supportedPlatforms: List<String> = emptyList(),
    val auth: List<RecipeAuthField> = emptyList(),
    val platformVars: Map<String, Map<String, String>> = emptyMap(),
    val qualityMap: Map<String, String> = emptyMap(),
    val request: RecipeRequest,
    val extract: RecipeExtract,
    val validate: RecipeValidate? = null
)

@Serializable
data class RecipeAuthField(
    val key: String,
    val label: String,
    val required: Boolean = true,
    val type: String = "text"
)

@Serializable
data class RecipeRequest(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: kotlinx.serialization.json.JsonElement? = null,
    val timeoutSeconds: Int = 15,
    val followRedirects: Boolean = true
)

@Serializable
data class RecipeExtract(
    val kind: String,
    val successCondition: RecipeSuccessCondition? = null,
    val playableUrlPath: String? = null,
    val errorMessagePath: String? = null
)

@Serializable
data class RecipeSuccessCondition(
    val path: String,
    val equals: kotlinx.serialization.json.JsonElement? = null,
    val notEquals: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class RecipeValidate(
    val titleSimilarity: RecipeTitleSimilarity? = null
)

@Serializable
data class RecipeTitleSimilarity(
    val responsePath: String,
    val trackField: String = "title",
    val min: Float = 0.4f
)
