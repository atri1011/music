package com.music.myapplication.feature.more.audiosource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.data.repository.recipe.RecipeRegistry
import com.music.myapplication.data.repository.recipe.RecipeValidator
import com.music.myapplication.domain.model.AudioSourceDescriptor
import com.music.myapplication.domain.model.AudioSourceId
import com.music.myapplication.domain.model.PlayableUrlRecipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioSourceManagementUiState(
    val currentSourceId: String = AudioSourceId.TUNEHUB.value,
    val allDescriptors: List<AudioSourceDescriptor> = emptyList(),
    val isImporting: Boolean = false,
    val importError: String? = null,
    val importSuccess: Boolean = false,
    val showImportDialog: Boolean = false,
    val importJsonText: String = "",
    val showAuthDialog: Boolean = false,
    val authRecipeId: String? = null,
    val authFields: List<AuthFieldUi> = emptyList(),
    val showDeleteConfirm: Boolean = false,
    val deleteTargetId: String? = null
)

data class AuthFieldUi(
    val key: String,
    val label: String,
    val value: String = "",
    val type: String = "text"
)

@HiltViewModel
class AudioSourceManagementViewModel @Inject constructor(
    private val preferences: PlayerPreferences,
    private val recipeRegistry: RecipeRegistry,
    private val recipeValidator: RecipeValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioSourceManagementUiState())
    val uiState: StateFlow<AudioSourceManagementUiState> = _uiState.asStateFlow()

    private val _authValues = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    init {
        viewModelScope.launch {
            recipeRegistry.initialize()
            combine(
                preferences.audioSourceId,
                recipeRegistry.recipes
            ) { sourceId, _ ->
                val descriptors = recipeRegistry.allDescriptors()
                sourceId to descriptors
            }.collect { (sourceId, descriptors) ->
                _uiState.update { state ->
                    state.copy(
                        currentSourceId = sourceId,
                        allDescriptors = descriptors
                    )
                }
            }
        }
    }

    fun selectSource(sourceId: String) {
        viewModelScope.launch {
            preferences.setAudioSource(sourceId)
        }
    }

    fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importJsonText = "", importError = null, importSuccess = false) }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false, importJsonText = "", importError = null) }
    }

    fun updateImportJsonText(text: String) {
        _uiState.update { it.copy(importJsonText = text, importError = null) }
    }

    fun importRecipe() {
        val text = _uiState.value.importJsonText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(importError = "JSON 不能为空") }
            return
        }

        val result = recipeValidator.validateJson(text)
        if (!result.isValid) {
            _uiState.update { it.copy(importError = result.errors.joinToString("\n")) }
            return
        }

        val recipe = try {
            kotlinx.serialization.json.Json.decodeFromString<PlayableUrlRecipe>(text)
        } catch (e: Exception) {
            _uiState.update { it.copy(importError = "解析失败: ${e.message}") }
            return
        }

        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            recipeRegistry.addOrUpdateRecipe(recipe)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importSuccess = true,
                    importError = null,
                    showImportDialog = false,
                    importJsonText = ""
                )
            }
        }
    }

    fun showAuthDialog(recipeId: String) {
        val recipe = recipeRegistry.recipes.value.find { it.id == recipeId } ?: return
        val storedAuth = _authValues.value[recipeId] ?: emptyMap()
        val fields = recipe.auth.map { field ->
            AuthFieldUi(
                key = field.key,
                label = field.label,
                value = storedAuth[field.key] ?: loadAuthFromPreferences(recipeId, field.key),
                type = field.type
            )
        }
        _uiState.update { it.copy(showAuthDialog = true, authRecipeId = recipeId, authFields = fields) }
    }

    fun dismissAuthDialog() {
        _uiState.update { it.copy(showAuthDialog = false, authRecipeId = null, authFields = emptyList()) }
    }

    fun updateAuthField(key: String, value: String) {
        _uiState.update { state ->
            state.copy(
                authFields = state.authFields.map { if (it.key == key) it.copy(value = value) else it }
            )
        }
    }

    fun saveAuthFields() {
        val recipeId = _uiState.value.authRecipeId ?: return
        val fields = _uiState.value.authFields
        val valuesMap = fields.associate { it.key to it.value }
        _authValues.update { it + (recipeId to valuesMap) }
        saveAuthToPreferences(recipeId, valuesMap)
        dismissAuthDialog()
    }

    fun showDeleteConfirm(recipeId: String) {
        _uiState.update { it.copy(showDeleteConfirm = true, deleteTargetId = recipeId) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false, deleteTargetId = null) }
    }

    fun confirmDelete() {
        val recipeId = _uiState.value.deleteTargetId ?: return
        viewModelScope.launch {
            recipeRegistry.deleteRecipe(recipeId)
            if (_uiState.value.currentSourceId == recipeId) {
                preferences.setAudioSource(AudioSourceId.TUNEHUB.value)
            }
            dismissDeleteConfirm()
        }
    }

    fun getAuthValuesForRecipe(recipeId: String): Map<String, String> {
        return _authValues.value[recipeId] ?: emptyMap()
    }

    private fun loadAuthFromPreferences(recipeId: String, key: String): String {
        return preferences.getRecipeAuthValue(recipeId, key)
    }

    private fun saveAuthToPreferences(recipeId: String, values: Map<String, String>) {
        viewModelScope.launch {
            preferences.saveRecipeAuthValues(recipeId, values)
        }
    }
}
