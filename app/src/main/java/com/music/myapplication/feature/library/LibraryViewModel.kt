package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val favorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val topPlayedTracks: List<Pair<Track, Int>> = emptyList(),
    val totalPlayCount: Int = 0,
    val totalListenDurationMs: Long = 0L,
    val downloadedCount: Int = 0,
    val localTrackCount: Int = 0,
    val showCreateDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val isImporting: Boolean = false,
    val importError: String? = null,
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
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiExtras = MutableStateFlow(LibraryExtras())

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
        _uiExtras
    ) { favorites, topPlayed, playlists, stats, extras ->
        LibraryUiState(
            favorites = favorites,
            playlists = playlists,
            topPlayedTracks = topPlayed,
            totalPlayCount = stats.totalPlayCount,
            totalListenDurationMs = stats.totalListenDurationMs,
            downloadedCount = stats.downloadedCount,
            localTrackCount = stats.localTrackCount,
            showCreateDialog = extras.showCreateDialog,
            showImportDialog = extras.showImportDialog,
            isImporting = extras.isImporting,
            importError = extras.importError,
            importedPlaylist = extras.importedPlaylist
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    fun showCreateDialog(show: Boolean) = _uiExtras.update { it.copy(showCreateDialog = show) }
    fun showImportDialog(show: Boolean) = _uiExtras.update {
        it.copy(
            showImportDialog = show,
            isImporting = false,
            importError = null
        )
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            localRepo.createPlaylist(name)
            _uiExtras.update { it.copy(showCreateDialog = false) }
        }
    }

    fun consumeImportedPlaylist() = _uiExtras.update { it.copy(importedPlaylist = null) }

    fun importPlaylist(platform: Platform, rawInput: String, customName: String) {
        viewModelScope.launch {
            val resolvedInput = resolveImportedPlaylistImportInput(platform, rawInput)
            if (resolvedInput == null) {
                _uiExtras.update {
                    it.copy(
                        showImportDialog = true,
                        isImporting = false,
                        importError = importedPlaylistInputError(rawInput)
                    )
                }
                return@launch
            }

            _uiExtras.update {
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
                            _uiExtras.update {
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
                        _uiExtras.update {
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
                        _uiExtras.update {
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
                _uiExtras.update {
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

    private suspend fun resolveImportedPlaylistImportInput(
        selectedPlatform: Platform,
        rawInput: String
    ): ImportedPlaylistInput? {
        resolveImportedPlaylistInput(selectedPlatform, rawInput)?.let { return it }
        if (!looksLikeWebUrl(rawInput)) return null

        val resolvedUrl = onlineRepo.resolveShareUrl(rawInput)
        if (resolvedUrl.isBlank()) return null
        return resolveImportedPlaylistInput(selectedPlatform, resolvedUrl)
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { localRepo.deletePlaylist(playlistId) }
    }

    private data class LibraryExtras(
        val showCreateDialog: Boolean = false,
        val showImportDialog: Boolean = false,
        val isImporting: Boolean = false,
        val importError: String? = null,
        val importedPlaylist: ImportedPlaylistDestination? = null
    )

    private data class StatsBundle(
        val totalPlayCount: Int,
        val totalListenDurationMs: Long,
        val downloadedCount: Int,
        val localTrackCount: Int
    )
}

internal data class ImportedPlaylistInput(
    val platform: Platform,
    val playlistId: String
)

internal fun resolveImportedPlaylistInput(
    selectedPlatform: Platform,
    rawInput: String
): ImportedPlaylistInput? {
    val resolvedPlatform = detectImportedPlatform(rawInput) ?: selectedPlatform
    val playlistId = extractImportedPlaylistId(resolvedPlatform, rawInput) ?: return null
    return ImportedPlaylistInput(
        platform = resolvedPlatform,
        playlistId = playlistId
    )
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

private fun importedPlaylistInputError(rawInput: String): String {
    return if (looksLikeSongLink(rawInput)) {
        "这里只支持导入歌单，不支持单曲链接。你填歌单分享链接或者歌单 ID 就行。"
    } else {
        "没识别出来歌单链接或歌单 ID。确认别填成单曲 ID，老老实实贴歌单分享链接或者歌单 ID。"
    }
}

private fun appendImportedPlaylistHint(message: String, rawInput: String): String {
    val normalizedMessage = message.trim().ifBlank { "导入歌单失败，稍后再试。" }
    val hint = when {
        looksLikeSongLink(rawInput) -> "这里只支持歌单，不支持单曲链接。"
        rawInput.trim().all(Char::isDigit) -> "确认填的是歌单 ID，不是单曲 ID。"
        else -> ""
    }
    return listOf(normalizedMessage, hint)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun detectImportedPlatform(rawInput: String): Platform? {
    val normalizedInput = rawInput.trim().lowercase()
    return when {
        "music.163.com" in normalizedInput || "163cn.tv" in normalizedInput -> Platform.NETEASE
        "y.qq.com" in normalizedInput || "qq.com" in normalizedInput -> Platform.QQ
        "kuwo.cn" in normalizedInput -> Platform.KUWO
        else -> null
    }
}

private fun looksLikeWebUrl(rawInput: String): Boolean {
    val normalizedInput = rawInput.trim()
    return normalizedInput.startsWith("http://", ignoreCase = true) ||
        normalizedInput.startsWith("https://", ignoreCase = true)
}

private fun looksLikeSongLink(rawInput: String): Boolean {
    val normalizedInput = rawInput.trim()
    val songPatterns = listOf(
        Regex("""songDetail/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE),
        Regex("""(?:^|[#/])song\?id=\d+""", RegexOption.IGNORE_CASE),
        Regex("""/song/\d+""", RegexOption.IGNORE_CASE),
        Regex("""/play_detail/\d+""", RegexOption.IGNORE_CASE)
    )
    return songPatterns.any { it.containsMatchIn(normalizedInput) }
}
