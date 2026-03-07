package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
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
    val showImportDialog: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localRepo: LocalLibraryRepository
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
            showImportDialog = extras.showImportDialog
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun showCreateDialog(show: Boolean) = _uiExtras.update { it.copy(showCreateDialog = show) }
    fun showImportDialog(show: Boolean) = _uiExtras.update { it.copy(showImportDialog = show) }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            localRepo.createPlaylist(name)
            _uiExtras.update { it.copy(showCreateDialog = false) }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { localRepo.deletePlaylist(playlistId) }
    }

    private data class LibraryExtras(
        val showCreateDialog: Boolean = false,
        val showImportDialog: Boolean = false
    )

    private data class StatsBundle(
        val totalPlayCount: Int,
        val totalListenDurationMs: Long
    )
}
