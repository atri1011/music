package com.music.myapplication.feature.more.audiosource

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.AudioSourceDescriptor
import com.music.myapplication.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSourceManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLxSources: () -> Unit = {},
    viewModel: AudioSourceManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音源管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.Medium)
        ) {
            Text(
                text = "当前播放音源",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            state.allDescriptors.forEach { descriptor ->
                AudioSourceDescriptorItem(
                    descriptor = descriptor,
                    isSelected = descriptor.id.value == state.currentSourceId,
                    onSelect = { viewModel.selectSource(descriptor.id.value) },
                    onConfigure = {
                        when (descriptor) {
                            is AudioSourceDescriptor.Native.LxCustom -> onNavigateToLxSources()
                            is AudioSourceDescriptor.Recipe -> viewModel.showAuthDialog(descriptor.recipe.id)
                            else -> Unit
                        }
                    },
                    onDelete = {
                        if (descriptor is AudioSourceDescriptor.Recipe) {
                            viewModel.showDeleteConfirm(descriptor.recipe.id)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(AppSpacing.Medium))

            TextButton(onClick = viewModel::showImportDialog) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("导入 Recipe")
            }

            Spacer(modifier = Modifier.height(AppSpacing.XLarge))
        }
    }

    if (state.showImportDialog) {
        ImportRecipeDialog(
            jsonText = state.importJsonText,
            onJsonTextChange = viewModel::updateImportJsonText,
            onImport = viewModel::importRecipe,
            onDismiss = viewModel::dismissImportDialog,
            isImporting = state.isImporting,
            error = state.importError
        )
    }

    if (state.showAuthDialog && state.authRecipeId != null) {
        AuthConfigDialog(
            recipeId = state.authRecipeId!!,
            fields = state.authFields,
            onFieldValueChange = viewModel::updateAuthField,
            onSave = viewModel::saveAuthFields,
            onDismiss = viewModel::dismissAuthDialog
        )
    }

    if (state.showDeleteConfirm && state.deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("删除音源") },
            text = { Text("确定要删除此音源吗？删除后将回退到 TuneHub。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AudioSourceDescriptorItem(
    descriptor: AudioSourceDescriptor,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit
) {
    val isNative = descriptor is AudioSourceDescriptor.Native
    val needsConfig = when (descriptor) {
        is AudioSourceDescriptor.Native.LxCustom -> true
        is AudioSourceDescriptor.Recipe -> descriptor.recipe.auth.isNotEmpty()
        else -> false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = descriptor.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "支持平台：${descriptor.supportedPlatforms.joinToString("、") { it.displayName }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (descriptor is AudioSourceDescriptor.Native.TuneHub) {
                    Text(
                        text = "推荐/默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!isNative) {
                    Text(
                        text = "Recipe 音源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (needsConfig) {
                IconButton(onClick = onConfigure) {
                    Icon(Icons.Default.Key, contentDescription = "配置", modifier = Modifier.size(20.dp))
                }
            }
            if (!isNative) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportRecipeDialog(
    jsonText: String,
    onJsonTextChange: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    isImporting: Boolean,
    error: String?
) {
    val context = LocalContext.current
    var importMode by remember { mutableStateOf("paste") }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                if (text != null) onJsonTextChange(text)
            } catch (_: Exception) { }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 Recipe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { importMode = "paste" }) {
                        Text(if (importMode == "paste") "● 粘贴 JSON" else "粘贴 JSON")
                    }
                    TextButton(onClick = {
                        importMode = "file"
                        fileLauncher.launch("application/json")
                    }) {
                        Text(if (importMode == "file") "● 选择文件" else "选择文件")
                    }
                }
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = onJsonTextChange,
                    label = { Text("Recipe JSON") },
                    placeholder = { Text("粘贴或选择 JSON 文件") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 20,
                    isError = error != null
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = !isImporting) {
                Text(if (isImporting) "导入中..." else "导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AuthConfigDialog(
    recipeId: String,
    fields: List<AuthFieldUi>,
    onFieldValueChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置音源参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { onFieldValueChange(field.key, it) },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (fields.isEmpty()) {
                    Text("此音源无需额外配置", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
