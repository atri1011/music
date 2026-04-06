package com.music.myapplication.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.data.repository.lx.LxCustomScript
import com.music.myapplication.data.repository.lx.LxCustomScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LxSourcesUiState(
    val scripts: List<LxCustomScript> = emptyList(),
    val activeScriptId: String? = null,
    val isEditing: Boolean = false,
    val editingScriptId: String? = null,
    val editingScriptText: String = "",
    val isSaving: Boolean = false,
    val isProcessing: Boolean = false,
    val validationErrorScript: LxCustomScript? = null,
    val pendingDeleteScript: LxCustomScript? = null,
    val statusMessage: String? = null,
    val statusMessageId: Long = 0L
) {
    val editingTitle: String
        get() = if (editingScriptId == null) "新增脚本" else "编辑脚本"
}

private data class LxSourcesEditorState(
    val isEditing: Boolean = false,
    val scriptId: String? = null,
    val scriptText: String = ""
)

private data class LxSourcesTransientState(
    val isSaving: Boolean = false,
    val isProcessing: Boolean = false,
    val validationErrorScript: LxCustomScript? = null,
    val pendingDeleteScript: LxCustomScript? = null,
    val statusMessage: String? = null,
    val statusMessageId: Long = 0L
)

@HiltViewModel
class LxSourcesViewModel @Inject constructor(
    private val scriptRepository: LxCustomScriptRepository
) : ViewModel() {
    private val editorState = MutableStateFlow(LxSourcesEditorState())
    private val transientState = MutableStateFlow(LxSourcesTransientState())

    val state: StateFlow<LxSourcesUiState> = combine(
        scriptRepository.catalog,
        editorState,
        transientState
    ) { catalog, editor, transient ->
        LxSourcesUiState(
            scripts = catalog.scripts.sortedByDescending { it.updatedAt },
            activeScriptId = catalog.activeScriptId,
            isEditing = editor.isEditing,
            editingScriptId = editor.scriptId,
            editingScriptText = editor.scriptText,
            isSaving = transient.isSaving,
            isProcessing = transient.isProcessing,
            validationErrorScript = transient.validationErrorScript,
            pendingDeleteScript = transient.pendingDeleteScript,
            statusMessage = transient.statusMessage,
            statusMessageId = transient.statusMessageId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LxSourcesUiState())

    fun startCreate() {
        editorState.value = LxSourcesEditorState(isEditing = true)
    }

    fun startEdit(script: LxCustomScript) {
        editorState.value = LxSourcesEditorState(
            isEditing = true,
            scriptId = script.id,
            scriptText = script.rawScript
        )
    }

    fun updateEditingScript(text: String) {
        editorState.update { it.copy(scriptText = text) }
    }

    fun loadImportedScript(
        rawScript: String,
        fileName: String? = null
    ) {
        val normalizedScript = rawScript.removePrefix("\uFEFF")
        if (normalizedScript.isBlank()) {
            transientState.update {
                it.copy(
                    statusMessage = "导入失败：所选文件内容为空",
                    statusMessageId = System.currentTimeMillis()
                )
            }
            return
        }

        val currentEditor = editorState.value
        editorState.value = LxSourcesEditorState(
            isEditing = true,
            scriptId = currentEditor.scriptId.takeIf { currentEditor.isEditing },
            scriptText = normalizedScript
        )
        transientState.update {
            it.copy(
                statusMessage = buildImportedScriptMessage(
                    fileName = fileName,
                    keepExistingScript = currentEditor.isEditing && currentEditor.scriptId != null
                ),
                statusMessageId = System.currentTimeMillis()
            )
        }
    }

    fun cancelEditing() {
        editorState.value = LxSourcesEditorState()
    }

    fun saveEditing() {
        val current = editorState.value
        if (!current.isEditing || transientState.value.isSaving) return
        viewModelScope.launch {
            transientState.update { it.copy(isSaving = true) }
            when (
                val result = scriptRepository.saveScript(
                    rawScript = current.scriptText,
                    existingScriptId = current.scriptId
                )
            ) {
                is Result.Success -> {
                    val saved = result.data
                    editorState.value = LxSourcesEditorState()
                    transientState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = if (saved.isValidationPassed) {
                                "脚本已保存并通过校验"
                            } else {
                                "脚本已保存为草稿，但校验失败：${saved.lastValidationError}"
                            },
                            statusMessageId = System.currentTimeMillis()
                        )
                    }
                }
                is Result.Error -> {
                    transientState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = result.error.message,
                            statusMessageId = System.currentTimeMillis()
                        )
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun activateScript(scriptId: String) {
        if (transientState.value.isProcessing) return
        viewModelScope.launch {
            transientState.update { it.copy(isProcessing = true) }
            val statusMessage = try {
                when (val result = scriptRepository.activateScript(scriptId)) {
                    is Result.Success -> "已切换当前脚本：${result.data.displayTitle}"
                    is Result.Error -> result.error.message
                    Result.Loading -> null
                }
            } catch (error: Throwable) {
                error.message?.ifBlank { "切换当前脚本失败" } ?: "切换当前脚本失败"
            }
            transientState.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = statusMessage,
                    statusMessageId = System.currentTimeMillis()
                )
            }
        }
    }

    fun requestDelete(script: LxCustomScript) {
        transientState.update { it.copy(pendingDeleteScript = script) }
    }

    fun cancelDelete() {
        transientState.update { it.copy(pendingDeleteScript = null) }
    }

    fun confirmDelete() {
        val target = transientState.value.pendingDeleteScript ?: return
        if (transientState.value.isProcessing) return
        viewModelScope.launch {
            transientState.update { it.copy(isProcessing = true, pendingDeleteScript = null) }
            scriptRepository.deleteScript(target.id)
            transientState.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = "已删除脚本：${target.displayTitle}",
                    statusMessageId = System.currentTimeMillis()
                )
            }
        }
    }

    fun showValidationError(script: LxCustomScript) {
        transientState.update { it.copy(validationErrorScript = script) }
    }

    fun dismissValidationError() {
        transientState.update { it.copy(validationErrorScript = null) }
    }

    fun consumeStatusMessage() {
        transientState.update { it.copy(statusMessage = null) }
    }

    private fun buildImportedScriptMessage(
        fileName: String?,
        keepExistingScript: Boolean
    ): String {
        val sourceLabel = fileName?.takeIf { it.isNotBlank() } ?: "本地文件"
        return if (keepExistingScript) {
            "已载入 $sourceLabel，保存后会覆盖当前脚本"
        } else {
            "已载入 $sourceLabel，确认后保存并校验"
        }
    }
}
