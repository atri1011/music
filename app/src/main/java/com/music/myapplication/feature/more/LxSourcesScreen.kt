package com.music.myapplication.feature.more

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.data.repository.lx.LxCustomScript
import com.music.myapplication.data.repository.lx.toLxSourceDisplayText
import com.music.myapplication.feature.components.EmptyStateView
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxSourcesScreen(
    onBack: () -> Unit,
    viewModel: LxSourcesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isImportingFile by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isImportingFile = true
            val result = runCatching {
                withContext(Dispatchers.IO) { readImportedLxScript(context, uri) }
            }
            isImportingFile = false
            result.onSuccess { imported ->
                viewModel.loadImportedScript(
                    rawScript = imported.rawScript,
                    fileName = imported.displayName
                )
            }.onFailure { error ->
                snackbarHostState.showSnackbar(
                    error.message?.ifBlank { "读取脚本文件失败" } ?: "读取脚本文件失败"
                )
            }
        }
    }
    val launchImportPicker = remember(importLauncher) {
        { importLauncher.launch(arrayOf("*/*")) }
    }

    LaunchedEffect(state.statusMessageId) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatusMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("落雪脚本管理")
                        Text(
                            text = "支持粘贴或导入本地 JS；保存时会立即校验。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!state.isEditing) {
                        IconButton(onClick = viewModel::startCreate) {
                            Icon(Icons.Default.Add, contentDescription = "新增脚本")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isEditing) {
            LxScriptEditor(
                state = state,
                onTextChange = viewModel::updateEditingScript,
                onImportFromFile = launchImportPicker,
                onCancel = viewModel::cancelEditing,
                onSave = viewModel::saveEditing,
                isImportingFile = isImportingFile,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else if (state.scripts.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Code,
                title = "还没保存任何脚本",
                subtitle = "先粘贴一份 LX Music 自定义源原始 JS，或者直接导入本地文件。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::startCreate) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("新增脚本", modifier = Modifier.padding(start = 8.dp))
                        }
                        FilledTonalButton(
                            onClick = launchImportPicker,
                            enabled = !isImportingFile
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null)
                            Text(
                                text = if (isImportingFile) "导入中…" else "导入本地 JS",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = viewModel::startCreate) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("新增脚本", modifier = Modifier.padding(start = 8.dp))
                        }
                        FilledTonalButton(
                            onClick = launchImportPicker,
                            enabled = !isImportingFile
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null)
                            Text(
                                text = if (isImportingFile) "导入中…" else "导入本地 JS",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                items(
                    items = state.scripts,
                    key = { it.id }
                ) { script ->
                    LxScriptCard(
                        script = script,
                        isActive = script.id == state.activeScriptId,
                        isBusy = state.isProcessing,
                        onActivate = { viewModel.activateScript(script.id) },
                        onEdit = { viewModel.startEdit(script) },
                        onDelete = { viewModel.requestDelete(script) },
                        onShowError = { viewModel.showValidationError(script) }
                    )
                }
            }
        }
    }

    state.validationErrorScript?.let { script ->
        AlertDialog(
            onDismissRequest = viewModel::dismissValidationError,
            title = { Text("校验错误") },
            text = {
                Text(
                    text = script.lastValidationError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissValidationError) {
                    Text("知道了")
                }
            }
        )
    }

    state.pendingDeleteScript?.let { script ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("删除脚本") },
            text = { Text("确认删除 `${script.displayTitle}`？如果它正是当前脚本，会一并取消激活。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LxScriptEditor(
    state: LxSourcesUiState,
    onTextChange: (String) -> Unit,
    onImportFromFile: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    isImportingFile: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = state.editingTitle,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "直接粘贴原始脚本，或者从本地文件载入。保存后会解析头部元信息并执行一次 inited 校验；即使失败也会作为草稿保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = onImportFromFile,
            enabled = !state.isSaving && !isImportingFile
        ) {
            Icon(Icons.Default.Code, contentDescription = null)
            Text(
                text = if (isImportingFile) "载入中…" else "从本地文件载入",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        OutlinedTextField(
            value = state.editingScriptText,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 10,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text(
                    text = if (state.isSaving) "保存中…" else "保存并校验",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            TextButton(
                onClick = onCancel,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun LxScriptCard(
    script: LxCustomScript,
    isActive: Boolean,
    isBusy: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowError: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script.displayTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val meta = listOfNotNull(
                        script.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                        script.author.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val statusText = when {
                    isActive && script.isValidationPassed -> "当前脚本"
                    isActive -> "当前脚本（不可用）"
                    script.isValidationPassed -> "已通过校验"
                    else -> "校验失败"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (script.isValidationPassed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            if (script.description.isNotBlank()) {
                Text(
                    text = script.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "声明来源：${script.declaredSources.toLxSourceDisplayText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (script.homepage.isNotBlank()) {
                Text(
                    text = script.homepage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onActivate,
                    enabled = script.isValidationPassed && !isActive && !isBusy
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.OfflineBolt else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Text(
                        text = if (isActive) "当前使用中" else "设为当前",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                TextButton(onClick = onEdit, enabled = !isBusy) {
                    Text("编辑")
                }
                TextButton(onClick = onDelete, enabled = !isBusy) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Text("删除", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (!script.isValidationPassed && script.lastValidationError != null) {
                TextButton(onClick = onShowError, enabled = !isBusy) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null)
                    Text("查看校验错误", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private data class ImportedLxScript(
    val displayName: String?,
    val rawScript: String
)

private fun readImportedLxScript(
    context: Context,
    uri: Uri
): ImportedLxScript {
    val displayName = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex >= 0) cursor.getString(columnIndex) else null
    }

    val rawScript = context.contentResolver.openInputStream(uri)
        ?.bufferedReader(UTF_8)
        ?.use { reader -> reader.readText() }
        ?.removePrefix("\uFEFF")
        ?: throw IllegalStateException("无法读取所选脚本文件")

    if (rawScript.isBlank()) {
        throw IllegalStateException("所选脚本文件内容为空")
    }

    return ImportedLxScript(
        displayName = displayName,
        rawScript = rawScript
    )
}
