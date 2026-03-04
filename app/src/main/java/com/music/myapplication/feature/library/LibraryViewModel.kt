package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val favorites: List<Track> = emptyList(),
    val recentPlays: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedTab: Int = 0,
    val showCreateDialog: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val apiKey: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val localRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences
) : ViewModel() {

    private val _uiExtras = MutableStateFlow(LibraryExtras())
    val state: StateFlow<LibraryUiState> = combine(
        localRepo.getFavorites(),
        localRepo.getRecentPlays(),
        localRepo.getPlaylists(),
        preferences.apiKey,
        _uiExtras
    ) { favorites, recents, playlists, apiKey, extras ->
        LibraryUiState(
            favorites = favorites,
            recentPlays = recents,
            playlists = playlists,
            selectedTab = extras.selectedTab,
            showCreateDialog = extras.showCreateDialog,
            showApiKeyDialog = extras.showApiKeyDialog,
            apiKey = apiKey
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun selectTab(index: Int) = _uiExtras.update { it.copy(selectedTab = index) }
    fun showCreateDialog(show: Boolean) = _uiExtras.update { it.copy(showCreateDialog = show) }
    fun showApiKeyDialog(show: Boolean) = _uiExtras.update { it.copy(showApiKeyDialog = show) }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            localRepo.createPlaylist(name)
            _uiExtras.update { it.copy(showCreateDialog = false) }
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            preferences.setApiKey(apiKey)
            _uiExtras.update { it.copy(showApiKeyDialog = false) }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch { localRepo.deletePlaylist(playlistId) }
    }

    private data class LibraryExtras(
        val selectedTab: Int = 0,
        val showCreateDialog: Boolean = false,
        val showApiKeyDialog: Boolean = false
    )
}
