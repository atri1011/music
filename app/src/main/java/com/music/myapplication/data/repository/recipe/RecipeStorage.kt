package com.music.myapplication.data.repository.recipe

import android.content.Context
import com.music.myapplication.domain.model.PlayableUrlRecipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val recipesDir: File
        get() = File(context.filesDir, "recipes")

    suspend fun loadAll(): List<PlayableUrlRecipe> = withContext(Dispatchers.IO) {
        val dir = recipesDir
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<PlayableUrlRecipe>(file.readText()) }.getOrNull()
            }
            ?: emptyList()
    }

    suspend fun save(recipe: PlayableUrlRecipe): Unit = withContext(Dispatchers.IO) {
        val dir = recipesDir
        dir.mkdirs()
        val file = File(dir, "${recipe.id}.json")
        file.writeText(json.encodeToString(PlayableUrlRecipe.serializer(), recipe))
    }

    suspend fun delete(recipeId: String): Unit = withContext(Dispatchers.IO) {
        val file = File(recipesDir, "$recipeId.json")
        if (file.exists()) file.delete()
    }

    suspend fun loadBuiltinRecipes(): List<PlayableUrlRecipe> = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        runCatching {
            assetManager.list("builtin_recipes")
                ?.filter { it.endsWith(".json") }
                ?.mapNotNull { name ->
                    runCatching {
                        val text = assetManager.open("builtin_recipes/$name").bufferedReader().use { it.readText() }
                        json.decodeFromString<PlayableUrlRecipe>(text)
                    }.getOrNull()
                }
        }.getOrNull() ?: emptyList()
    }
}
