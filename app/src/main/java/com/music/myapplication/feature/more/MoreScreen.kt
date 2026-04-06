package com.music.myapplication.feature.more

import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.BuildConfig
import com.music.myapplication.core.datastore.DarkModeOption
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.data.repository.lx.toLxSourceDisplayText
import com.music.myapplication.domain.model.AudioSource
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.feature.update.AppUpdateActionState
import com.music.myapplication.feature.update.AppUpdateUiState
import com.music.myapplication.feature.update.AppUpdateViewModel
import com.music.myapplication.ui.theme.AppSpacing

@Composable
fun MoreScreen(
    viewModel: MoreViewModel = hiltViewModel(),
    onNavigateToLxSources: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? ComponentActivity
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    val updateViewModel: AppUpdateViewModel = if (activity != null) {
        hiltViewModel(viewModelStoreOwner = activity)
    } else {
        hiltViewModel()
    }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshCacheUsage()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = AppSpacing.Large, vertical = AppSpacing.Medium)
        )

        // ── 播放 ──
        SettingsGroup(title = "播放") {
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "音质",
                subtitle = qualityLabel(state.quality),
                onClick = {}
            ) {
                QualityPicker(
                    current = state.quality,
                    onSelect = viewModel::setQuality
                )
            }
            SettingsItem(
                icon = Icons.Default.Repeat,
                title = "播放模式",
                subtitle = playbackModeLabel(state.playbackMode),
                onClick = {}
            ) {
                PlaybackModePicker(
                    current = state.playbackMode,
                    onSelect = viewModel::setPlaybackMode
                )
            }
            SwitchSettingsItem(
                icon = Icons.Default.PlayArrow,
                title = "自动播放",
                subtitle = "打开应用时恢复上次播放",
                checked = state.autoPlay,
                onCheckedChange = viewModel::setAutoPlay
            )
            SwitchSettingsItem(
                icon = Icons.Default.SwapHoriz,
                title = "Crossfade（POC）",
                subtitle = crossfadeStatusLabel(state),
                checked = state.crossfadeEnabled,
                onCheckedChange = viewModel::setCrossfadeEnabled
            )
            SettingsItem(
                icon = Icons.Default.Timer,
                title = "Crossfade 时长",
                subtitle = crossfadeDurationLabel(state.crossfadeDurationMs),
                onClick = {},
                enabled = state.crossfadeEnabled
            ) {
                CrossfadeDurationPicker(
                    current = state.crossfadeDurationMs,
                    onSelect = viewModel::setCrossfadeDurationMs
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Small))

        // ── 外观 ──
        SettingsGroup(title = "外观") {
            SettingsItem(
                icon = Icons.Default.Brightness6,
                title = "深色模式",
                subtitle = darkModeLabel(state.darkMode),
                onClick = {}
            ) {
                DarkModePicker(
                    current = state.darkMode,
                    onSelect = viewModel::setDarkMode
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Small))

        // ── 网络 ──
        SettingsGroup(title = "网络") {
            SwitchSettingsItem(
                icon = Icons.Default.Wifi,
                title = "仅 WiFi 播放与下载",
                subtitle = "移动网络下不自动播放，也不发起歌曲下载",
                checked = state.wifiOnly,
                onCheckedChange = viewModel::setWifiOnly
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.Small))

        // ── 存储 ──
        SettingsGroup(title = "存储") {
            SettingsItem(
                icon = Icons.Default.Cached,
                title = "缓存上限",
                subtitle = "${state.cacheLimitMb} MB",
                onClick = {}
            ) {
                CacheLimitPicker(
                    current = state.cacheLimitMb,
                    onSelect = viewModel::setCacheLimitMb
                )
            }
            SettingsItem(
                icon = Icons.Default.Image,
                title = "图片缓存",
                subtitle = cacheItemSubtitle(state.imageCacheBytes, state.isCacheLoading),
                onClick = {}
            )
            SettingsItem(
                icon = Icons.Default.Subtitles,
                title = "歌词缓存",
                subtitle = cacheItemSubtitle(state.lyricsCacheBytes, state.isCacheLoading),
                onClick = {}
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "模板缓存",
                subtitle = cacheItemSubtitle(state.templateCacheBytes, state.isCacheLoading),
                onClick = {}
            )
            SettingsItem(
                icon = Icons.Default.DeleteOutline,
                title = "清理缓存",
                subtitle = clearCacheSubtitle(state),
                onClick = viewModel::clearCache,
                enabled = !state.isCacheLoading && !state.isClearingCache && state.totalCacheBytes > 0L
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.Small))

        // ── 音源与账户 ──
        SettingsGroup(title = "音源与账户") {
            SettingsItem(
                icon = Icons.Default.SwapHoriz,
                title = "播放音源",
                subtitle = audioSourceSubtitle(state),
                onClick = { showAudioSourceDialog = true },
                showChevron = true
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "落雪脚本",
                subtitle = state.lxSourceSummary,
                onClick = onNavigateToLxSources,
                showChevron = true
            )
            SettingsItem(
                icon = Icons.Default.VpnKey,
                title = "TuneHub 密钥",
                subtitle = maskApiKey(state.apiKey),
                onClick = { viewModel.showApiKeyDialog(true) }
            )
            SettingsItem(
                icon = Icons.Default.Key,
                title = "JKAPI 密钥",
                subtitle = maskApiKey(state.jkapiKey),
                onClick = { viewModel.showJkapiKeyDialog(true) }
            )
            SettingsItem(
                icon = Icons.Default.Link,
                title = "增强版接口地址",
                subtitle = baseUrlSubtitle(state.neteaseCloudApiBaseUrl),
                onClick = { viewModel.showNeteaseCloudApiBaseUrlDialog(true) }
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.Small))

        // ── 关于 ──
        SettingsGroup(title = "关于") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = {}
            )
            SettingsItem(
                icon = Icons.Default.NewReleases,
                title = "检查更新",
                subtitle = updateCheckSubtitle(updateState),
                onClick = updateViewModel::checkNow,
                enabled = !updateState.isChecking
            )
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Music Player",
                subtitle = "基于 Kotlin + Jetpack Compose",
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.XLarge))
    }

    if (showAudioSourceDialog) {
        AudioSourceSelectionDialog(
            options = buildAudioSourceOptions(state),
            onDismiss = { showAudioSourceDialog = false },
            onSelect = { source ->
                showAudioSourceDialog = false
                viewModel.setAudioSource(source)
            },
            onConfigure = { source ->
                showAudioSourceDialog = false
                when (source) {
                    AudioSource.JKAPI -> viewModel.showJkapiKeyDialog(true)
                    AudioSource.LX_CUSTOM -> onNavigateToLxSources()
                    AudioSource.NETEASE_CLOUD_API_ENHANCED -> {
                        viewModel.showNeteaseCloudApiBaseUrlDialog(true)
                    }
                    else -> Unit
                }
            }
        )
    }

    if (state.showApiKeyDialog) {
        KeyInputDialog(
            title = "设置 TuneHub 密钥",
            label = "API Key",
            placeholder = "th_xxx",
            initialValue = state.apiKey,
            onDismiss = { viewModel.showApiKeyDialog(false) },
            onConfirm = viewModel::saveApiKey
        )
    }

    if (state.showJkapiKeyDialog) {
        KeyInputDialog(
            title = "设置 JKAPI 密钥",
            label = "API Key",
            placeholder = "输入 JKAPI 密钥",
            initialValue = state.jkapiKey,
            onDismiss = { viewModel.showJkapiKeyDialog(false) },
            onConfirm = viewModel::saveJkapiKey
        )
    }

    if (state.showNeteaseCloudApiBaseUrlDialog) {
        KeyInputDialog(
            title = "设置网易云增强版接口地址",
            label = "Base URL",
            placeholder = "https://your-project.vercel.app",
            initialValue = state.neteaseCloudApiBaseUrl,
            onDismiss = { viewModel.showNeteaseCloudApiBaseUrlDialog(false) },
            onConfirm = viewModel::saveNeteaseCloudApiBaseUrl
        )
    }
}

// ── Composable Building Blocks ──────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AppSpacing.Large, vertical = 6.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val alpha = if (enabled) 1f else 0.4f
    val showIndicator = showChevron || trailing != null

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    if (trailing != null) expanded = !expanded else onClick()
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (showIndicator) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (expanded && trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Inline Pickers ──────────────────────────────────────────────────────────

@Composable
private fun QualityPicker(current: String, onSelect: (String) -> Unit) {
    val options = listOf("128k" to "标准", "320k" to "高品质", "flac" to "无损")
    PickerRow(options = options, current = current, onSelect = onSelect)
}

@Composable
private fun PlaybackModePicker(current: PlaybackMode, onSelect: (PlaybackMode) -> Unit) {
    val options = listOf(
        PlaybackMode.SEQUENTIAL to "顺序播放",
        PlaybackMode.SHUFFLE to "随机播放",
        PlaybackMode.REPEAT_ONE to "单曲循环"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == current
            TextButton(onClick = { onSelect(mode) }) {
                Text(
                    text = if (selected) "● $label" else label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DarkModePicker(current: DarkModeOption, onSelect: (DarkModeOption) -> Unit) {
    val options = listOf(
        DarkModeOption.FOLLOW_SYSTEM to "跟随系统",
        DarkModeOption.DARK to "深色",
        DarkModeOption.LIGHT to "浅色"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (option, label) ->
            val selected = option == current
            TextButton(onClick = { onSelect(option) }) {
                Text(
                    text = if (selected) "● $label" else label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CacheLimitPicker(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf("200" to 200, "500" to 500, "1000" to 1000, "2000" to 2000)
    PickerRow(
        options = options.map { (label, value) -> value.toString() to "${label} MB" },
        current = current.toString(),
        onSelect = { onSelect(it.toInt()) }
    )
}

@Composable
private fun CrossfadeDurationPicker(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf(500, 1_000, 1_500, 2_000).map { duration ->
        duration.toString() to crossfadeDurationLabel(duration)
    }
    PickerRow(
        options = options,
        current = current.toString(),
        onSelect = { onSelect(it.toInt()) }
    )
}

@Composable
private fun PickerRow(options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == current
            TextButton(onClick = { onSelect(value) }) {
                Text(
                    text = if (selected) "● $label" else label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal data class AudioSourceOptionUi(
    val source: AudioSource,
    val title: String,
    val supportText: String,
    val configText: String,
    val fallbackText: String,
    val statusText: String,
    val isSelected: Boolean,
    val canSelect: Boolean,
    val configureActionLabel: String? = null
)

@Composable
internal fun AudioSourceSelectionDialog(
    options: List<AudioSourceOptionUi>,
    onDismiss: () -> Unit,
    onSelect: (AudioSource) -> Unit,
    onConfigure: (AudioSource) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择播放音源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { option ->
                    AudioSourceOptionItem(
                        option = option,
                        onSelect = onSelect,
                        onConfigure = onConfigure
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun AudioSourceOptionItem(
    option: AudioSourceOptionUi,
    onSelect: (AudioSource) -> Unit,
    onConfigure: (AudioSource) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (option.canSelect) onSelect(option.source) else onConfigure(option.source)
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = option.isSelected,
                onClick = if (option.canSelect) {
                    { onSelect(option.source) }
                } else {
                    null
                },
                enabled = option.canSelect
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                )
                if (option.statusText.isNotBlank()) {
                    Text(
                        text = option.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            option.configureActionLabel?.let { label ->
                if (!option.canSelect) {
                    TextButton(onClick = { onConfigure(option.source) }) {
                        Text(label)
                    }
                }
            }
        }
        Text(
            text = option.supportText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 52.dp, top = 2.dp)
        )
        Text(
            text = option.configText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 52.dp, top = 2.dp)
        )
        Text(
            text = option.fallbackText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 52.dp, top = 2.dp)
        )
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun KeyInputDialog(
    title: String,
    label: String,
    placeholder: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun maskApiKey(apiKey: String): String {
    if (apiKey.isBlank()) return "未设置"
    val trimmed = apiKey.trim()
    val visibleSuffix = trimmed.takeLast(4)
    return "已设置 · ****$visibleSuffix"
}

private fun qualityLabel(quality: String): String = when (quality) {
    "128k" -> "标准 128k"
    "320k" -> "高品质 320k"
    "flac" -> "无损 FLAC"
    else -> quality
}

private fun playbackModeLabel(mode: PlaybackMode): String = when (mode) {
    PlaybackMode.SEQUENTIAL -> "顺序播放"
    PlaybackMode.SHUFFLE -> "随机播放"
    PlaybackMode.REPEAT_ONE -> "单曲循环"
}

private fun darkModeLabel(option: DarkModeOption): String = when (option) {
    DarkModeOption.FOLLOW_SYSTEM -> "跟随系统"
    DarkModeOption.DARK -> "深色"
    DarkModeOption.LIGHT -> "浅色"
}

private fun crossfadeStatusLabel(state: MoreUiState): String = when {
    state.crossfadeEnabled -> "POC 模式，淡出/淡入各 ${crossfadeDurationLabel(state.crossfadeDurationMs)}"
    else -> "关闭时优先走顺序播放的无缝切歌"
}

private fun crossfadeDurationLabel(durationMs: Int): String {
    val clamped = durationMs.coerceIn(
        PlayerPreferences.CROSSFADE_MIN_DURATION_MS,
        PlayerPreferences.CROSSFADE_MAX_DURATION_MS
    )
    return if (clamped % 1_000 == 0) {
        "${clamped / 1_000} 秒"
    } else {
        String.format("%.1f 秒", clamped / 1_000f)
    }
}

internal fun buildAudioSourceOptions(state: MoreUiState): List<AudioSourceOptionUi> {
    return AudioSource.entries.map { source ->
        AudioSourceOptionUi(
            source = source,
            title = source.displayName,
            supportText = "支持平台：${audioSourceSupportedPlatformsText(source, state)}",
            configText = "额外配置：${audioSourceConfigText(source, state)}",
            fallbackText = "失败行为：${audioSourceFallbackText(source)}",
            statusText = audioSourceStatusText(source, state),
            isSelected = source == state.audioSource,
            canSelect = isAudioSourceSelectable(source, state),
            configureActionLabel = audioSourceConfigureActionLabel(source)
        )
    }
}

internal fun audioSourceSubtitle(state: MoreUiState): String {
    val source = state.audioSource
    val currentLine = buildString {
        append("当前：")
        append(source.displayName)
        audioSourceCurrentStatusSuffix(source, state)?.let { append(it) }
    }
    return listOf(
        currentLine,
        "支持：${audioSourceSupportedPlatformsText(source, state)}",
        "失败行为：${audioSourceFallbackText(source)}"
    ).joinToString("\n")
}

private fun audioSourceSupportedPlatformsText(source: AudioSource, state: MoreUiState): String = when (source) {
    AudioSource.LX_CUSTOM -> when {
        state.lxActiveScriptSources.isNotEmpty() -> state.lxActiveScriptSources.toLxSourceDisplayText()
        else -> "由当前脚本决定（网易云 / QQ / 酷我）"
    }
    else -> source.supportedPlatforms.joinToString("、") { it.displayName }
}

private fun audioSourceConfigText(source: AudioSource, state: MoreUiState): String = when (source) {
    AudioSource.TUNEHUB,
    AudioSource.METING_BAKA -> "无需额外配置"
    AudioSource.LX_CUSTOM -> when {
        state.lxScriptCount <= 0 -> "需至少保存 1 份脚本"
        state.canSelectLxCustom -> "当前脚本可用，共 ${state.lxValidScriptCount} 份通过校验"
        else -> "需激活 1 份通过校验且声明 wy/tx/kw 的脚本"
    }
    AudioSource.JKAPI -> {
        if (state.jkapiKey.isBlank()) "需 JKAPI 密钥（未配置）" else "JKAPI 密钥已配置"
    }
    AudioSource.NETEASE_CLOUD_API_ENHANCED -> {
        if (state.neteaseCloudApiBaseUrl.isBlank()) "需增强版接口地址（未配置）" else "增强版接口地址已配置"
    }
}

private fun audioSourceFallbackText(source: AudioSource): String = when (source) {
    AudioSource.TUNEHUB -> "当前即统一兜底音源"
    else -> "不可用时自动回退 TuneHub"
}

private fun audioSourceStatusText(source: AudioSource, state: MoreUiState): String {
    val labels = buildList {
        if (source == AudioSource.TUNEHUB) add("推荐/默认")
        if (source == state.audioSource) add("当前使用中")
        if (!isAudioSourceSelectable(source, state)) add("先配置再使用")
        if (source == AudioSource.LX_CUSTOM && state.lxActiveScriptValidationError != null) {
            add("当前脚本校验失败")
        }
    }
    return labels.joinToString(" · ")
}

private fun audioSourceCurrentStatusSuffix(source: AudioSource, state: MoreUiState): String? = when {
    source == AudioSource.TUNEHUB -> "（推荐/默认）"
    source == AudioSource.LX_CUSTOM && state.lxActiveScriptName.isBlank() -> "（先完成脚本配置）"
    !isAudioSourceSelectable(source, state) -> "（先完成配置）"
    else -> null
}

private fun isAudioSourceSelectable(source: AudioSource, state: MoreUiState): Boolean = when (source) {
    AudioSource.LX_CUSTOM -> state.canSelectLxCustom
    AudioSource.JKAPI -> state.jkapiKey.isNotBlank()
    AudioSource.NETEASE_CLOUD_API_ENHANCED -> state.neteaseCloudApiBaseUrl.isNotBlank()
    else -> true
}

private fun audioSourceConfigureActionLabel(source: AudioSource): String? = when (source) {
    AudioSource.LX_CUSTOM -> "配置脚本"
    AudioSource.JKAPI -> "配置密钥"
    AudioSource.NETEASE_CLOUD_API_ENHANCED -> "配置接口"
    else -> null
}

private fun baseUrlSubtitle(baseUrl: String): String = baseUrl.ifBlank { "未设置" }

private fun updateCheckSubtitle(state: AppUpdateUiState): String = when {
    state.isChecking -> "检查中..."
    state.actionState == AppUpdateActionState.DOWNLOADING -> {
        state.downloadProgressPercent?.let { "下载中：$it%" } ?: "下载中..."
    }
    state.actionState == AppUpdateActionState.DOWNLOAD_FAILED -> {
        state.stageMessage ?: "更新下载失败"
    }
    state.actionState == AppUpdateActionState.VERIFY_FAILED -> {
        state.stageMessage ?: "安装包校验失败"
    }
    state.actionState == AppUpdateActionState.INSTALL_READY -> {
        state.stageMessage ?: "安装包已就绪"
    }
    state.actionState == AppUpdateActionState.INSTALL_PERMISSION_REQUIRED -> {
        state.stageMessage ?: "请先完成未知来源安装授权"
    }
    state.actionState == AppUpdateActionState.INSTALLING -> {
        state.stageMessage ?: "已拉起安装器"
    }
    state.showDialog && state.availableUpdate != null -> "发现新版本：${state.availableUpdate.latestVersionName}"
    !state.lastCheckMessage.isNullOrBlank() -> state.lastCheckMessage!!
    else -> "点击检查"
}

private fun cacheItemSubtitle(sizeBytes: Long, isLoading: Boolean): String {
    return if (isLoading) "统计中..." else formatStorageSize(sizeBytes)
}

private fun clearCacheSubtitle(state: MoreUiState): String = when {
    state.isClearingCache -> "正在清理 ${formatStorageSize(state.totalCacheBytes)}"
    state.isCacheLoading -> "统计缓存中..."
    state.totalCacheBytes <= 0L -> "暂无可清理缓存"
    else -> "当前共 ${formatStorageSize(state.totalCacheBytes)}"
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes.toDouble() / gb)
        bytes >= mb -> String.format("%.2f MB", bytes.toDouble() / mb)
        bytes >= kb -> String.format("%.2f KB", bytes.toDouble() / kb)
        else -> "$bytes B"
    }
}
