package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
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
    val showCreateDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val isImporting: Boolean = false,
    val importError: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localRepo: LocalLibraryRepository,
    private val onlineRepo: OnlineMusicRepository
) : ViewModel() {

    private val _uiExtras = MutableStateFlow(LibraryExtras())

    private val statsFlow = combine(
        localRepo.getTotalPlayCount(),
        localRepo.getTotalListenDurationMs()
    ) { count, duration ->
        StatsBundle(count, duration)
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
            showCreateDialog = extras.showCreateDialog,
            showImportDialog = extras.showImportDialog,
            isImporting = extras.isImporting,
            importError = extras.importError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

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

    fun importPlaylist(platform: Platform, rawInput: String, customName: String) {
        viewModelScope.launch {
            val playlistId = extractImportedPlaylistId(platform, rawInput)
            if (playlistId == null) {
                _uiExtras.update {
                    it.copy(
                        showImportDialog = true,
                        isImporting = false,
                        importError = "没识别出来歌单链接或 ID，别整花活，直接贴分享链接或者纯数字 ID。"
                    )
                }
                return@launch
            }

            _uiExtras.update {
                it.copy(
                    showImportDialog = true,
                    isImporting = true,
                    importError = null
                )
            }

            when (val result = onlineRepo.getPlaylistDetail(platform, playlistId)) {
                is Result.Success -> {
                    val tracks = result.data
                    if (tracks.isEmpty()) {
                        _uiExtras.update {
                            it.copy(
                                showImportDialog = true,
                                isImporting = false,
                                importError = "歌单是空的，或者接口没给到可用歌曲。"
                            )
                        }
                        return@launch
                    }

                    val playlistName = customName.trim()
                        .ifBlank { defaultImportedPlaylistName(platform, playlistId) }
                    val playlist = localRepo.createPlaylist(playlistName)
                    localRepo.addAllToPlaylist(playlist.id, tracks)
                    _uiExtras.update {
                        it.copy(
                            showImportDialog = false,
                            isImporting = false,
                            importError = null
                        )
                    }
                }
                is Result.Error -> {
                    _uiExtras.update {
                        it.copy(
                            showImportDialog = true,
                            isImporting = false,
                            importError = result.error.message
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { localRepo.deletePlaylist(playlistId) }
    }

    private data class LibraryExtras(
        val showCreateDialog: Boolean = false,
        val showImportDialog: Boolean = false,
        val isImporting: Boolean = false,
        val importError: String? = null
    )

    private data class StatsBundle(
        val totalPlayCount: Int,
        val totalListenDurationMs: Long
    )
}

internal fun extractImportedPlaylistId(platform: Platform, rawInput: String): String? {
    val input = rawInput.trim()
    if (input.isBlank()) return null
    if (input.all(Char::isDigit)) return input

    val patterns = when (platform) {
        Platform.QQ -> listOf(
            Regex("""playlist/(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""songDetail/(\d+)""", RegexOption.IGNORE_CASE),
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
