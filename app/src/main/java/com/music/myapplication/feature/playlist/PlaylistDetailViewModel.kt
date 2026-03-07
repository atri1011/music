package com.music.myapplication.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val title: String = ""
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistDetailUiState())
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    fun loadPlaylist(id: String, platform: String, title: String, source: String = "toplist") {
        _state.update { it.copy(isLoading = true, error = null, title = title) }
        viewModelScope.launch {
            if (platform == "local") {
                localRepo.getPlaylistSongs(id).collect { tracks ->
                    _state.update { it.copy(tracks = tracks, isLoading = false) }
                }
            } else {
                val p = Platform.fromId(platform)
                when (source) {
                    "playlist" -> {
                        when (val result = onlineRepo.getPlaylistDetail(p, id)) {
                            is Result.Success -> {
                                val enriched = localRepo.applyFavoriteState(result.data)
                                _state.update { it.copy(tracks = enriched, isLoading = false) }
                            }
                            is Result.Error -> {
                                _state.update { it.copy(error = result.error.message, isLoading = false) }
                            }
                            is Result.Loading -> {}
                        }
                    }
                    else -> {
                        when (val result = onlineRepo.getToplistDetailFast(p, id)) {
                            is Result.Success -> {
                                val baseTracks = localRepo.applyFavoriteState(result.data)
                                _state.update { it.copy(tracks = baseTracks, isLoading = false) }

                                if (p == Platform.QQ || p == Platform.KUWO) {
                                    launch {
                                        val hydrated = onlineRepo.enrichToplistTracks(p, id, result.data)
                                        val hydratedTracks = localRepo.applyFavoriteState(hydrated)
                                        if (hydratedTracks != baseTracks) {
                                            _state.update { it.copy(tracks = hydratedTracks) }
                                        }
                                    }
                                }
                            }
                            is Result.Error -> {
                                _state.update { it.copy(error = result.error.message, isLoading = false) }
                            }
                            is Result.Loading -> {}
                        }
                    }
                }
            }
        }
    }
}
