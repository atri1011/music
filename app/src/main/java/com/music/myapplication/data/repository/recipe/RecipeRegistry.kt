package com.music.myapplication.data.repository.recipe

import com.music.myapplication.domain.model.AudioSourceDescriptor
import com.music.myapplication.domain.model.AudioSourceId
import com.music.myapplication.domain.model.PlayableUrlRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRegistry @Inject constructor(
    private val storage: RecipeStorage
) {
    private val _recipes = MutableStateFlow<List<PlayableUrlRecipe>>(emptyList())
    val recipes: StateFlow<List<PlayableUrlRecipe>> = _recipes.asStateFlow()

    private val nativeDescriptors = listOf(
        AudioSourceDescriptor.Native.TuneHub(),
        AudioSourceDescriptor.Native.LxCustom()
    )
    private val initializeMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initializeMutex.withLock {
            if (initialized) return@withLock
            val builtin = storage.loadBuiltinRecipes()
            val user = storage.loadAll()
            _recipes.value = mergeRecipes(builtin, user)
            initialized = true
        }
    }

    fun find(id: AudioSourceId): AudioSourceDescriptor? {
        val native = nativeDescriptors.find { it.id == id }
        if (native != null) return native
        val recipe = _recipes.value.find { it.id == id.value }
        if (recipe != null) return AudioSourceDescriptor.Recipe(recipe)
        return null
    }

    fun tuneHubDescriptor(): AudioSourceDescriptor.Native.TuneHub = AudioSourceDescriptor.Native.TuneHub()

    fun allDescriptors(): List<AudioSourceDescriptor> {
        return nativeDescriptors + _recipes.value.map { AudioSourceDescriptor.Recipe(it) }
    }

    suspend fun addOrUpdateRecipe(recipe: PlayableUrlRecipe) {
        storage.save(recipe)
        _recipes.value = mergeRecipes(_recipes.value, listOf(recipe))
    }

    suspend fun deleteRecipe(recipeId: String) {
        storage.delete(recipeId)
        _recipes.value = _recipes.value.filter { it.id != recipeId }
    }

    fun isBuiltin(recipeId: String): Boolean {
        return _recipes.value.any { it.id == recipeId }
    }

    private fun mergeRecipes(builtin: List<PlayableUrlRecipe>, user: List<PlayableUrlRecipe>): List<PlayableUrlRecipe> {
        val byId = linkedMapOf<String, PlayableUrlRecipe>()
        for (recipe in builtin) {
            byId[recipe.id] = recipe
        }
        for (recipe in user) {
            byId[recipe.id] = recipe
        }
        return byId.values.toList()
    }
}
