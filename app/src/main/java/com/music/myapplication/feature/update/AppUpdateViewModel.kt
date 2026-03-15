package com.music.myapplication.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
    val lastCheckMessage: String? = null
)

private const val UPDATE_CHECK_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val preferences: PlayerPreferences,
    private val repository: AppUpdateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        checkIfNeeded()
    }

    private fun checkIfNeeded() = runCheck(force = false, userInitiated = false)

    fun checkNow() = runCheck(force = true, userInitiated = true)

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
                                        lastCheckMessage = "已是最新版本"
                                    )
                                }
                            }
                            return@launch
                        }

                        if (!userInitiated) {
                            val lastNotified = preferences.appUpdateLastNotifiedVersionCode.first()
                            if (lastNotified >= update.latestVersionCode) return@launch
                        }

                        _state.update { current ->
                            current.copy(
                                availableUpdate = update,
                                showDialog = true,
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
                    else -> {}
                }
            } finally {
                if (userInitiated) {
                    _state.update { it.copy(isChecking = false) }
                }
            }
        }
    }

    fun dismiss(versionCode: Int) {
        viewModelScope.launch {
            preferences.setAppUpdateLastNotifiedVersionCode(versionCode)
            _state.update { it.copy(showDialog = false, availableUpdate = null) }
        }
    }
}

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("最新版本：${update.latestVersionName}", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                update.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                    Text(changelog, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateNow) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("稍后")
            }
        }
    )
}
