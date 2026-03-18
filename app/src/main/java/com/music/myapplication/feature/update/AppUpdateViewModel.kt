package com.music.myapplication.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.BuildConfig
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.AppUpdateInfo
import com.music.myapplication.domain.repository.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val availableUpdate: AppUpdateInfo? = null,
    val showDialog: Boolean = false,
    val isChecking: Boolean = false,
    val lastCheckMessage: String? = null,
    val canSkipUpdate: Boolean = true,
    val actionState: AppUpdateActionState = AppUpdateActionState.IDLE,
    val downloadProgressPercent: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val pendingInstallFilePath: String? = null,
    val stageMessage: String? = null
)

enum class AppUpdateActionState {
    IDLE,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    VERIFY_FAILED,
    INSTALL_READY,
    INSTALLING,
    INSTALL_PERMISSION_REQUIRED
}

private const val UPDATE_CHECK_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val preferences: PlayerPreferences,
    private val repository: AppUpdateRepository,
    private val downloadCoordinator: AppUpdateDownloadCoordinator,
    private val installer: AppUpdateInstaller
) : ViewModel() {

    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var lastHandledDownloadedFilePath: String? = null

    init {
        observeDownloadState()
        checkIfNeeded()
    }

    private fun checkIfNeeded() = runCheck(force = false, userInitiated = false)

    fun checkNow() = runCheck(force = true, userInitiated = true)

    fun onPrimaryAction() {
        val current = _state.value
        val update = current.availableUpdate ?: return
        when (current.actionState) {
            AppUpdateActionState.DOWNLOADING -> Unit
            AppUpdateActionState.INSTALL_READY,
            AppUpdateActionState.INSTALL_PERMISSION_REQUIRED,
            AppUpdateActionState.INSTALLING -> {
                val filePath = current.pendingInstallFilePath.orEmpty()
                if (filePath.isNotBlank()) {
                    launchInstall(filePath)
                } else {
                    startDownload(update)
                }
            }
            AppUpdateActionState.DOWNLOAD_FAILED,
            AppUpdateActionState.VERIFY_FAILED,
            AppUpdateActionState.IDLE -> startDownload(update)
        }
    }

    fun dismissCurrentUpdate() {
        val current = _state.value
        val update = current.availableUpdate ?: return
        if (!current.canSkipUpdate) return

        viewModelScope.launch {
            preferences.setAppUpdateLastNotifiedVersionCode(update.latestVersionCode)
            if (current.actionState == AppUpdateActionState.DOWNLOADING) {
                downloadCoordinator.cancel()
            }
            _state.update {
                it.copy(
                    showDialog = false,
                    availableUpdate = null,
                    canSkipUpdate = true,
                    actionState = AppUpdateActionState.IDLE,
                    downloadProgressPercent = null,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    pendingInstallFilePath = null,
                    stageMessage = null
                )
            }
        }
    }

    private fun runCheck(force: Boolean, userInitiated: Boolean) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!force) {
                val lastCheckAt = preferences.appUpdateLastCheckAtMs.first()
                if (now - lastCheckAt < UPDATE_CHECK_INTERVAL_MS) return@launch
            }

            if (userInitiated) {
                _state.update { it.copy(isChecking = true, lastCheckMessage = null) }
            }

            try {
                when (val result = repository.fetchLatest()) {
                    is Result.Success -> {
                        val update = result.data
                        if (update == null) {
                            if (userInitiated) {
                                _state.update {
                                    it.copy(
                                        availableUpdate = null,
                                        showDialog = false,
                                        lastCheckMessage = "未配置更新源地址"
                                    )
                                }
                            }
                            return@launch
                        }

                        preferences.setAppUpdateLastCheckAtMs(now)

                        if (update.latestVersionCode <= BuildConfig.VERSION_CODE) {
                            if (userInitiated) {
                                _state.update {
                                    it.copy(
                                        availableUpdate = null,
                                        showDialog = false,
                                        lastCheckMessage = "已是最新版本",
                                        actionState = AppUpdateActionState.IDLE,
                                        downloadProgressPercent = null,
                                        downloadedBytes = 0L,
                                        totalBytes = 0L,
                                        pendingInstallFilePath = null,
                                        stageMessage = null
                                    )
                                }
                            }
                            return@launch
                        }

                        val canSkipUpdate = update.isSkipAllowed(BuildConfig.VERSION_CODE)
                        if (!userInitiated && canSkipUpdate) {
                            val lastNotified = preferences.appUpdateLastNotifiedVersionCode.first()
                            if (lastNotified >= update.latestVersionCode) return@launch
                        }

                        _state.update { current ->
                            current.copy(
                                availableUpdate = update,
                                showDialog = true,
                                canSkipUpdate = canSkipUpdate,
                                actionState = AppUpdateActionState.IDLE,
                                downloadProgressPercent = null,
                                downloadedBytes = 0L,
                                totalBytes = 0L,
                                pendingInstallFilePath = null,
                                stageMessage = if (!canSkipUpdate) {
                                    "该版本为强制更新，需完成安装后继续使用"
                                } else {
                                    null
                                },
                                lastCheckMessage = if (userInitiated) {
                                    "发现新版本：${update.latestVersionName}"
                                } else {
                                    current.lastCheckMessage
                                }
                            )
                        }
                    }
                    is Result.Error -> {
                        if (userInitiated) {
                            _state.update {
                                it.copy(
                                    availableUpdate = null,
                                    showDialog = false,
                                    lastCheckMessage = result.error.message.ifBlank { "更新检查失败" }
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            } finally {
                if (userInitiated) {
                    _state.update { it.copy(isChecking = false) }
                }
            }
        }
    }

    private fun observeDownloadState() {
        viewModelScope.launch {
            downloadCoordinator.observeDownloadState().collect { downloadState ->
                when (downloadState.status) {
                    AppUpdateDownloadStatus.IDLE -> Unit
                    AppUpdateDownloadStatus.ENQUEUED,
                    AppUpdateDownloadStatus.RUNNING -> {
                        _state.update {
                            it.copy(
                                actionState = AppUpdateActionState.DOWNLOADING,
                                downloadProgressPercent = downloadState.progressPercent,
                                downloadedBytes = downloadState.downloadedBytes,
                                totalBytes = downloadState.totalBytes,
                                stageMessage = "正在下载更新包"
                            )
                        }
                    }
                    AppUpdateDownloadStatus.FAILED -> {
                        _state.update {
                            it.copy(
                                actionState = AppUpdateActionState.DOWNLOAD_FAILED,
                                stageMessage = downloadState.errorMessage ?: "更新包下载失败，请重试",
                                downloadProgressPercent = null
                            )
                        }
                    }
                    AppUpdateDownloadStatus.SUCCEEDED -> {
                        handleDownloadSuccess(downloadState)
                    }
                }
            }
        }
    }

    private suspend fun handleDownloadSuccess(downloadState: AppUpdateDownloadState) {
        val filePath = downloadState.localFilePath?.trim().orEmpty()
        if (filePath.isBlank()) {
            _state.update {
                it.copy(
                    actionState = AppUpdateActionState.DOWNLOAD_FAILED,
                    stageMessage = "更新包下载完成但文件路径无效"
                )
            }
            return
        }

        val current = _state.value
        if (lastHandledDownloadedFilePath == filePath &&
            (current.actionState == AppUpdateActionState.INSTALL_READY ||
                current.actionState == AppUpdateActionState.INSTALLING ||
                current.actionState == AppUpdateActionState.INSTALL_PERMISSION_REQUIRED)
        ) {
            return
        }
        lastHandledDownloadedFilePath = filePath

        val update = current.availableUpdate
        if (update == null) {
            _state.update {
                it.copy(
                    actionState = AppUpdateActionState.INSTALL_READY,
                    pendingInstallFilePath = filePath,
                    stageMessage = "安装包已下载，点击继续安装"
                )
            }
            return
        }

        val verified = installer.verifySha256(filePath, update.sha256)
        if (!verified) {
            _state.update {
                it.copy(
                    actionState = AppUpdateActionState.VERIFY_FAILED,
                    pendingInstallFilePath = filePath,
                    stageMessage = "安装包校验失败，请重新下载"
                )
            }
            return
        }

        _state.update {
            it.copy(
                actionState = AppUpdateActionState.INSTALL_READY,
                pendingInstallFilePath = filePath,
                stageMessage = "安装包校验通过，准备安装"
            )
        }
        launchInstall(filePath)
    }

    private fun startDownload(update: AppUpdateInfo) {
        viewModelScope.launch {
            lastHandledDownloadedFilePath = null
            _state.update {
                it.copy(
                    actionState = AppUpdateActionState.DOWNLOADING,
                    stageMessage = "正在下载更新包",
                    downloadProgressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = update.fileSizeBytes,
                    pendingInstallFilePath = null
                )
            }

            runCatching {
                downloadCoordinator.enqueue(update)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        actionState = AppUpdateActionState.DOWNLOAD_FAILED,
                        stageMessage = throwable.message ?: "更新包下载任务启动失败"
                    )
                }
            }
        }
    }

    private fun launchInstall(filePath: String) {
        when (val result = installer.launchInstall(filePath)) {
            AppUpdateInstallResult.LaunchedInstaller -> {
                _state.update {
                    it.copy(
                        actionState = AppUpdateActionState.INSTALLING,
                        stageMessage = "已拉起系统安装器，请按提示完成安装"
                    )
                }
            }
            AppUpdateInstallResult.RequiresUnknownSourcePermission -> {
                _state.update {
                    it.copy(
                        actionState = AppUpdateActionState.INSTALL_PERMISSION_REQUIRED,
                        stageMessage = "请先允许安装未知来源应用，然后点击继续安装"
                    )
                }
            }
            is AppUpdateInstallResult.Failed -> {
                _state.update {
                    it.copy(
                        actionState = AppUpdateActionState.INSTALL_READY,
                        stageMessage = result.message
                    )
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    actionState: AppUpdateActionState,
    canSkipUpdate: Boolean,
    downloadProgressPercent: Int?,
    stageMessage: String?,
    onPrimaryAction: () -> Unit,
    onLater: () -> Unit
) {
    val confirmText = when (actionState) {
        AppUpdateActionState.DOWNLOADING -> "下载中..."
        AppUpdateActionState.INSTALL_READY,
        AppUpdateActionState.INSTALL_PERMISSION_REQUIRED,
        AppUpdateActionState.INSTALLING -> "继续安装"
        AppUpdateActionState.DOWNLOAD_FAILED,
        AppUpdateActionState.VERIFY_FAILED -> "重新下载"
        AppUpdateActionState.IDLE -> "立即更新"
    }
    val confirmEnabled = actionState != AppUpdateActionState.DOWNLOADING

    AlertDialog(
        onDismissRequest = {
            if (canSkipUpdate) {
                onLater()
            }
        },
        title = { Text("发现新版本") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("最新版本：${update.latestVersionName}", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                update.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                    Text(changelog, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (!canSkipUpdate) {
                    Text("当前版本需要强制升级", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                stageMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (actionState == AppUpdateActionState.DOWNLOADING) {
                    if (downloadProgressPercent != null && downloadProgressPercent >= 0) {
                        LinearProgressIndicator(
                            progress = { downloadProgressPercent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("下载进度：$downloadProgressPercent%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPrimaryAction, enabled = confirmEnabled) {
                Text(confirmText)
            }
        },
        dismissButton = {
            if (canSkipUpdate) {
                TextButton(onClick = onLater) {
                    Text("稍后")
                }
            }
        }
    )
}
