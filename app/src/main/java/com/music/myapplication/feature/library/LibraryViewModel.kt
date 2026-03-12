package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.common.ShareUtils
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.domain.model.NeteaseAccountSession
import com.music.myapplication.domain.model.NeteaseQrLoginPayload
import com.music.myapplication.domain.model.NeteaseQrLoginState
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.NeteaseAccountRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val favorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val topPlayedTracks: List<Pair<Track, Int>> = emptyList(),
    val totalPlayCount: Int = 0,
    val totalListenDurationMs: Long = 0L,
    val downloadedCount: Int = 0,
    val localTrackCount: Int = 0,
    val account: NeteaseAccountSession? = null,
    val isNeteaseConfigured: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val showLoginSheet: Boolean = false,
    val isImporting: Boolean = false,
    val isAuthenticating: Boolean = false,
    val isSendingCaptcha: Boolean = false,
    val isPollingQr: Boolean = false,
    val isSyncingNeteaseData: Boolean = false,
    val importError: String? = null,
    val authError: String? = null,
    val syncError: String? = null,
    val syncMessage: String? = null,
    val qrPayload: NeteaseQrLoginPayload? = null,
    val qrStatusMessage: String? = null,
    val importedPlaylist: ImportedPlaylistDestination? = null
)

data class ImportedPlaylistDestination(
    val playlistId: String,
    val playlistName: String
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localRepo: LocalLibraryRepository,
    private val onlineRepo: OnlineMusicRepository,
    private val downloadManager: DownloadManager,
    private val neteaseAccountRepository: NeteaseAccountRepository
) : ViewModel() {

    private val uiExtras = MutableStateFlow(LibraryExtras())
    private var qrPollingJob: Job? = null

    private val statsFlow = combine(
        localRepo.getTotalPlayCount(),
        localRepo.getTotalListenDurationMs(),
        downloadManager.getDownloadedCount(),
        localRepo.getLocalTrackCount()
    ) { count, duration, dlCount, localCount ->
        StatsBundle(count, duration, dlCount, localCount)
    }

    val state: StateFlow<LibraryUiState> = combine(
        localRepo.getFavorites(),
        localRepo.getTopPlayedTracks(),
        localRepo.getPlaylists(),
        statsFlow,
        neteaseAccountRepository.session,
        neteaseAccountRepository.isConfigured,
        uiExtras
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val favorites = values[0] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val topPlayed = values[1] as List<Pair<Track, Int>>
        @Suppress("UNCHECKED_CAST")
        val playlists = values[2] as List<Playlist>
        val stats = values[3] as StatsBundle
        val account = values[4] as NeteaseAccountSession?
        val isConfigured = values[5] as Boolean
        val extras = values[6] as LibraryExtras
        LibraryUiState(
            favorites = favorites,
            playlists = playlists,
            topPlayedTracks = topPlayed,
            totalPlayCount = stats.totalPlayCount,
            totalListenDurationMs = stats.totalListenDurationMs,
            downloadedCount = stats.downloadedCount,
            localTrackCount = stats.localTrackCount,
            account = account,
            isNeteaseConfigured = isConfigured,
            showCreateDialog = extras.showCreateDialog,
            showImportDialog = extras.showImportDialog,
            showLoginSheet = extras.showLoginSheet,
            isImporting = extras.isImporting,
            isAuthenticating = extras.isAuthenticating,
            isSendingCaptcha = extras.isSendingCaptcha,
            isPollingQr = extras.isPollingQr,
            isSyncingNeteaseData = extras.isSyncingNeteaseData,
            importError = extras.importError,
            authError = extras.authError,
            syncError = extras.syncError,
            syncMessage = extras.syncMessage,
            qrPayload = extras.qrPayload,
            qrStatusMessage = extras.qrStatusMessage,
            importedPlaylist = extras.importedPlaylist
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    init {
        refreshNeteaseSession(syncOnSuccess = true)
    }

    override fun onCleared() {
        qrPollingJob?.cancel()
        super.onCleared()
    }

    fun showCreateDialog(show: Boolean) = uiExtras.update { it.copy(showCreateDialog = show) }

    fun showImportDialog(show: Boolean) = uiExtras.update {
        it.copy(
            showImportDialog = show,
            isImporting = false,
            importError = null
        )
    }

    fun showLoginSheet(show: Boolean) {
        if (!show) {
            qrPollingJob?.cancel()
        }
        uiExtras.update {
            it.copy(
                showLoginSheet = show,
                authError = null,
                qrStatusMessage = if (show) it.qrStatusMessage else null,
                qrPayload = if (show) it.qrPayload else null,
                isPollingQr = if (show) it.isPollingQr else false
            )
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            localRepo.createPlaylist(name)
            uiExtras.update { it.copy(showCreateDialog = false) }
        }
    }

    fun consumeImportedPlaylist() = uiExtras.update { it.copy(importedPlaylist = null) }

    fun importPlaylist(platform: Platform, rawInput: String, customName: String) {
        viewModelScope.launch {
            val resolvedInput = resolveImportedPlaylistImportInput(platform, rawInput)
            if (resolvedInput == null) {
                uiExtras.update {
                    it.copy(
                        showImportDialog = true,
                        isImporting = false,
                        importError = importedPlaylistInputError(rawInput)
                    )
                }
                return@launch
            }

            uiExtras.update {
                it.copy(
                    showImportDialog = true,
                    isImporting = true,
                    importError = null,
                    importedPlaylist = null
                )
            }

            try {
                val resolvedPlatform = resolvedInput.platform
                val playlistId = resolvedInput.playlistId
                when (val result = onlineRepo.getPlaylistDetail(resolvedPlatform, playlistId)) {
                    is Result.Success -> {
                        val tracks = result.data
                        if (tracks.isEmpty()) {
                            uiExtras.update {
                                it.copy(
                                    showImportDialog = true,
                                    isImporting = false,
                                    importError = appendImportedPlaylistHint(
                                        "歌单是空的，或者接口没给到可用歌曲。",
                                        rawInput
                                    )
                                )
                            }
                            return@launch
                        }

                        val playlistName = customName.trim()
                            .ifBlank { defaultImportedPlaylistName(resolvedPlatform, playlistId) }
                        val playlist = localRepo.createPlaylist(playlistName)
                        localRepo.addAllToPlaylist(playlist.id, tracks)
                        uiExtras.update {
                            it.copy(
                                showImportDialog = false,
                                isImporting = false,
                                importError = null,
                                importedPlaylist = ImportedPlaylistDestination(
                                    playlistId = playlist.id,
                                    playlistName = playlist.name
                                )
                            )
                        }
                    }
                    is Result.Error -> {
                        uiExtras.update {
                            it.copy(
                                showImportDialog = true,
                                isImporting = false,
                                importError = appendImportedPlaylistHint(result.error.message, rawInput)
                            )
                        }
                    }
                    is Result.Loading -> Unit
                }
            } catch (e: Exception) {
                uiExtras.update {
                    it.copy(
                        showImportDialog = true,
                        isImporting = false,
                        importError = appendImportedPlaylistHint(
                            e.message ?: "导入歌单失败，稍后再试。",
                            rawInput
                        )
                    )
                }
            }
        }
    }

    fun loginWithPassword(phone: String, password: String) {
        authenticate(
            action = { neteaseAccountRepository.loginWithPassword(phone.trim(), password) }
        )
    }

    fun sendCaptcha(phone: String) {
        viewModelScope.launch {
            uiExtras.update { it.copy(isSendingCaptcha = true, authError = null) }
            when (val result = neteaseAccountRepository.sendCaptcha(phone.trim())) {
                is Result.Success -> uiExtras.update {
                    it.copy(
                        isSendingCaptcha = false,
                        syncMessage = "验证码已发送，请留意短信"
                    )
                }
                is Result.Error -> uiExtras.update {
                    it.copy(
                        isSendingCaptcha = false,
                        authError = result.error.message
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun loginWithCaptcha(phone: String, captcha: String) {
        authenticate(
            action = { neteaseAccountRepository.loginWithCaptcha(phone.trim(), captcha.trim()) }
        )
    }

    fun prepareQrLogin() {
        viewModelScope.launch {
            qrPollingJob?.cancel()
            uiExtras.update {
                it.copy(
                    isAuthenticating = true,
                    authError = null,
                    qrPayload = null,
                    qrStatusMessage = null,
                    isPollingQr = false
                )
            }
            when (val result = neteaseAccountRepository.createQrLogin()) {
                is Result.Success -> {
                    val payload = result.data
                    uiExtras.update {
                        it.copy(
                            isAuthenticating = false,
                            qrPayload = payload,
                            qrStatusMessage = "请使用网易云音乐 App 扫码登录"
                        )
                    }
                    startQrPolling(payload.key)
                }
                is Result.Error -> uiExtras.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = result.error.message
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun syncNeteaseData() {
        viewModelScope.launch {
            uiExtras.update {
                it.copy(
                    isSyncingNeteaseData = true,
                    syncError = null,
                    syncMessage = null
                )
            }
            when (val result = neteaseAccountRepository.syncLocalLibrary()) {
                is Result.Success -> uiExtras.update {
                    it.copy(
                        isSyncingNeteaseData = false,
                        syncMessage = "已同步 ${result.data.syncedPlaylistCount} 个歌单，收藏 ${result.data.syncedFavoriteCount} 首"
                    )
                }
                is Result.Error -> uiExtras.update {
                    it.copy(
                        isSyncingNeteaseData = false,
                        syncError = result.error.toNeteaseSyncMessage()
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun logoutNeteaseAccount() {
        viewModelScope.launch {
            qrPollingJob?.cancel()
            neteaseAccountRepository.logout()
            uiExtras.update {
                it.copy(
                    showLoginSheet = false,
                    authError = null,
                    qrPayload = null,
                    qrStatusMessage = null,
                    isPollingQr = false,
                    syncMessage = "网易云账号已退出"
                )
            }
        }
    }

    fun clearSyncMessage() = uiExtras.update { it.copy(syncMessage = null, syncError = null) }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { localRepo.deletePlaylist(playlistId) }
    }

    fun updatePlaylistCover(playlistId: String, sourceUri: String) {
        viewModelScope.launch {
            localRepo.updatePlaylistCover(playlistId, sourceUri)
        }
    }

    private fun authenticate(action: suspend () -> Result<NeteaseAccountSession>) {
        viewModelScope.launch {
            uiExtras.update {
                it.copy(
                    isAuthenticating = true,
                    authError = null,
                    syncMessage = null
                )
            }
            when (val result = action()) {
                is Result.Success -> {
                    uiExtras.update {
                        it.copy(
                            isAuthenticating = false,
                            showLoginSheet = false,
                            qrPayload = null,
                            qrStatusMessage = null,
                            isPollingQr = false
                        )
                    }
                    syncNeteaseData()
                }
                is Result.Error -> uiExtras.update {
                    it.copy(
                        isAuthenticating = false,
                        authError = result.error.message
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun startQrPolling(key: String) {
        qrPollingJob?.cancel()
        qrPollingJob = viewModelScope.launch {
            uiExtras.update { it.copy(isPollingQr = true, authError = null) }
            while (true) {
                when (val result = neteaseAccountRepository.checkQrLogin(key)) {
                    is Result.Success -> {
                        val status = result.data
                        when (status.state) {
                            NeteaseQrLoginState.WAITING,
                            NeteaseQrLoginState.SCANNED -> {
                                uiExtras.update {
                                    it.copy(
                                        isPollingQr = true,
                                        qrStatusMessage = status.message
                                    )
                                }
                                delay(QR_POLL_INTERVAL_MS)
                            }
                            NeteaseQrLoginState.AUTHORIZED -> {
                                uiExtras.update {
                                    it.copy(
                                        isPollingQr = false,
                                        showLoginSheet = false,
                                        qrPayload = null,
                                        qrStatusMessage = null
                                    )
                                }
                                syncNeteaseData()
                                return@launch
                            }
                            NeteaseQrLoginState.EXPIRED -> {
                                uiExtras.update {
                                    it.copy(
                                        isPollingQr = false,
                                        qrStatusMessage = status.message
                                    )
                                }
                                return@launch
                            }
                        }
                    }
                    is Result.Error -> {
                        uiExtras.update {
                            it.copy(
                                isPollingQr = false,
                                authError = result.error.message
                            )
                        }
                        return@launch
                    }
                    is Result.Loading -> delay(QR_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun refreshNeteaseSession(syncOnSuccess: Boolean) {
        viewModelScope.launch {
            when (val result = neteaseAccountRepository.refreshLoginStatus()) {
                is Result.Success -> {
                    if (result.data != null && syncOnSuccess) {
                        syncNeteaseData()
                    }
                }
                is Result.Error -> uiExtras.update {
                    it.copy(syncError = result.error.toNeteaseSessionRefreshMessage())
                }
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun resolveImportedPlaylistImportInput(
        selectedPlatform: Platform,
        rawInput: String
    ): ImportedPlaylistInput? {
        resolveImportedPlaylistInput(selectedPlatform, rawInput)?.let { return it }
        if (ShareUtils.extractShareUrlCandidates(rawInput).isEmpty()) return null

        val resolvedUrl = onlineRepo.resolveShareUrl(rawInput)
        if (resolvedUrl.isBlank()) return null
        return resolveImportedPlaylistInput(selectedPlatform, resolvedUrl)
    }

    private data class LibraryExtras(
        val showCreateDialog: Boolean = false,
        val showImportDialog: Boolean = false,
        val showLoginSheet: Boolean = false,
        val isImporting: Boolean = false,
        val isAuthenticating: Boolean = false,
        val isSendingCaptcha: Boolean = false,
        val isPollingQr: Boolean = false,
        val isSyncingNeteaseData: Boolean = false,
        val importError: String? = null,
        val authError: String? = null,
        val syncError: String? = null,
        val syncMessage: String? = null,
        val qrPayload: NeteaseQrLoginPayload? = null,
        val qrStatusMessage: String? = null,
        val importedPlaylist: ImportedPlaylistDestination? = null
    )

    private data class StatsBundle(
        val totalPlayCount: Int,
        val totalListenDurationMs: Long,
        val downloadedCount: Int,
        val localTrackCount: Int
    )

    private companion object {
        const val QR_POLL_INTERVAL_MS = 2_000L
    }
}

internal data class ImportedPlaylistInput(
    val platform: Platform,
    val playlistId: String
)

internal fun resolveImportedPlaylistInput(
    selectedPlatform: Platform,
    rawInput: String
): ImportedPlaylistInput? {
    return importedPlaylistInputCandidates(rawInput)
        .firstNotNullOfOrNull { candidate ->
            val resolvedPlatform = detectImportedPlatform(candidate) ?: selectedPlatform
            val playlistId = extractImportedPlaylistId(resolvedPlatform, candidate)
                ?: return@firstNotNullOfOrNull null
            ImportedPlaylistInput(
                platform = resolvedPlatform,
                playlistId = playlistId
            )
        }
}

internal fun extractImportedPlaylistId(platform: Platform, rawInput: String): String? {
    val input = rawInput.trim()
    if (input.isBlank()) return null
    if (looksLikeSongLink(input)) return null
    if (input.all(Char::isDigit)) return input

    val patterns = when (platform) {
        Platform.QQ -> listOf(
            Regex("""playlist/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""[?&](?:id|disstid)=(\d+)""", RegexOption.IGNORE_CASE)
        )
        Platform.NETEASE -> listOf(
            Regex("""playlist/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""[?&]id=(\d+)""", RegexOption.IGNORE_CASE)
        )
        Platform.KUWO -> listOf(
            Regex("""playlist(?:_detail)?/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""[?&](?:id|pid)=(\d+)""", RegexOption.IGNORE_CASE)
        )
        Platform.LOCAL -> return null
    }

    patterns.forEach { regex ->
        regex.find(input)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return Regex("""(?<!\d)(\d{5,})(?!\d)""")
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
}

private fun defaultImportedPlaylistName(platform: Platform, playlistId: String): String {
    return "${platform.displayName}歌单-$playlistId"
}

private fun importedPlaylistInputCandidates(rawInput: String): List<String> {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) return emptyList()
    return buildList {
        add(trimmed)
        addAll(ShareUtils.extractShareUrlCandidates(rawInput))
    }.distinct()
}

private fun detectImportedPlatform(input: String): Platform? {
    val value = input.lowercase()
    return when {
        "music.163.com" in value || "y.music.163.com" in value || "网易云" in value -> Platform.NETEASE
        "qq.com" in value || "@qq音乐" in value || "qq音乐" in value -> Platform.QQ
        "kuwo.cn" in value || "酷我" in value -> Platform.KUWO
        else -> null
    }
}

private fun looksLikeSongLink(input: String): Boolean {
    val value = input.lowercase()
    return listOf("songdetail", "/song/", "song?id=", "program?id=").any { it in value }
}

private fun importedPlaylistInputError(rawInput: String): String =
    appendImportedPlaylistHint("没识别出歌单链接或歌单 ID。", rawInput)

private fun appendImportedPlaylistHint(message: String, rawInput: String): String {
    val hint = ShareUtils.extractShareUrlCandidates(rawInput).firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { " 可识别链接片段：$it" }
        .orEmpty()
    return message + hint
}

private fun AppError.toNeteaseSessionRefreshMessage(): String = when (this) {
    is AppError.Network -> "网易云登录状态校验失败，已保留本地登录信息，请稍后重试。"
    is AppError.Api -> when (code) {
        301 -> "网易云登录已失效，请重新登录。"
        else -> "网易云连接异常，已保留本地登录信息，请稍后重试。"
    }
    else -> "网易云状态检查出了点问题，已保留本地登录信息，请稍后重试。"
}

private fun AppError.toNeteaseSyncMessage(): String = when (this) {
    is AppError.Network -> "同步网易云失败，网络有点飘，请稍后重试。"
    is AppError.Api -> when (code) {
        301 -> "网易云登录已失效，请重新登录后再同步。"
        else -> message.ifBlank { "同步网易云失败，请稍后重试。" }
    }
    else -> message.ifBlank { "同步网易云失败，请稍后重试。" }
}
