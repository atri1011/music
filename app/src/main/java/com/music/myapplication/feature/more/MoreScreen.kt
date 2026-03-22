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
import com.music.myapplication.domain.model.AudioSource
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.feature.update.AppUpdateActionState
import com.music.myapplication.feature.update.AppUpdateUiState
import com.music.myapplication.feature.update.AppUpdateViewModel
import com.music.myapplication.ui.theme.AppSpacing

@Composable
fun MoreScreen(
    viewModel: MoreViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? ComponentActivity
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
                subtitle = audioSourceSubtitle(state.audioSource),
                onClick = {}
            ) {
                AudioSourcePicker(
                    current = state.audioSource,
                    onSelect = viewModel::setAudioSource
                )
            }
            SettingsItem(
                icon = Icons.Default.VpnKey,
                title = "TuneHub 密钥",
                subtitle = maskApiKey(state.apiKey),
                onClick = { viewModel.showApiKeyDialog(true) }
            )
            if (state.audioSource == AudioSource.JKAPI) {
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "JKAPI 密钥",
                    subtitle = maskApiKey(state.jkapiKey),
                    onClick = { viewModel.showJkapiKeyDialog(true) }
                )
            }
            if (state.audioSource == AudioSource.NETEASE_CLOUD_API_ENHANCED) {
                SettingsItem(
                    icon = Icons.Default.Link,
                    title = "增强版接口地址",
                    subtitle = baseUrlSubtitle(state.neteaseCloudApiBaseUrl),
                    onClick = { viewModel.showNeteaseCloudApiBaseUrlDialog(true) }
                )
            }
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
    trailing: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val alpha = if (enabled) 1f else 0.4f

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
            if (trailing != null) {
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
private fun AudioSourcePicker(current: AudioSource, onSelect: (AudioSource) -> Unit) {
    val options = listOf(
        AudioSource.TUNEHUB to "TuneHub",
        AudioSource.JKAPI to "JKAPI (无铭API)",
        AudioSource.NETEASE_CLOUD_API_ENHANCED to "网易云增强版 API"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Medium, vertical = AppSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (source, label) ->
            val selected = source == current
            TextButton(onClick = { onSelect(source) }) {
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
    else -> "关闭后回退普通切歌"
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

private fun audioSourceSubtitle(source: AudioSource): String = when (source) {
    AudioSource.TUNEHUB -> "默认音源，支持全平台"
    AudioSource.JKAPI -> "支持网易云/QQ音乐（不支持酷我）"
    AudioSource.NETEASE_CLOUD_API_ENHANCED -> "仅代理网易云歌曲，需填写自部署接口地址"
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
