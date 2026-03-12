package com.music.myapplication.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val tracks: List<Track> = emptyList(),
    val editingTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isSavingEdits: Boolean = false,
    val isLocalPlaylist: Boolean = false,
    val isFavoritesCollection: Boolean = false,
    val isEditMode: Boolean = false,
    val error: String? = null,
    val editMessage: String? = null,
    val title: String = "",
    val coverUrl: String = ""
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository
) : ViewModel() {

    private companion object {
        const val SOURCE_FAVORITES = "favorites"
    }

    private val _state = MutableStateFlow(PlaylistDetailUiState())
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var currentPlaylistId: String? = null

    fun loadPlaylist(id: String, platform: String, title: String, source: String = "toplist") {
        currentPlaylistId = id
        loadJob?.cancel()
        val isLocalPlaylist = platform == Platform.LOCAL.id
        _state.update {
            it.copy(
                isLoading = true,
                isSavingEdits = false,
                isLocalPlaylist = isLocalPlaylist,
                isFavoritesCollection = source == SOURCE_FAVORITES,
                isEditMode = false,
                editingTracks = emptyList(),
                error = null,
                editMessage = null,
                title = title
            )
        }
        loadJob = viewModelScope.launch {
            if (source == SOURCE_FAVORITES) {
                localRepo.getFavorites().collect { tracks ->
                    _state.update { current ->
                        current.copy(
                            title = title.ifBlank { "收藏" },
                            coverUrl = tracks.firstOrNull()?.coverUrl.orEmpty(),
                            tracks = tracks,
                            editingTracks = emptyList(),
                            isLoading = false,
                            isLocalPlaylist = false,
                            isFavoritesCollection = true,
                            isEditMode = false
                        )
                    }
                }
            } else if (platform == "local") {
                val playlist = localRepo.getPlaylistById(id)
                _state.update {
                    it.copy(
                        title = playlist?.name ?: title,
                        coverUrl = playlist?.coverUrl.orEmpty()
                    )
                }
                localRepo.getPlaylistSongs(id).collect { tracks ->
                    _state.update { current ->
                        current.copy(
                            tracks = tracks,
                            editingTracks = if (current.isEditMode) current.editingTracks else tracks,
                            isLoading = false,
                            isLocalPlaylist = true,
                            isFavoritesCollection = false,
                            coverUrl = current.coverUrl.ifBlank { playlist?.coverUrl.orEmpty() }
                        )
                    }
                }
            } else {
                val p = Platform.fromId(platform)
                when (source) {
                    "playlist" -> {
                        when (val result = onlineRepo.getPlaylistDetail(p, id)) {
                            is Result.Success -> {
                                val enriched = localRepo.applyFavoriteState(result.data)
                                _state.update {
                                    it.copy(
                                        tracks = enriched,
                                        editingTracks = emptyList(),
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false
                                    )
                                }
                            }
                            is Result.Error -> {
                                _state.update {
                                    it.copy(
                                        error = result.error.message,
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false
                                    )
                                }
                            }
                            is Result.Loading -> {}
                        }
                    }
                    else -> {
                        when (val result = onlineRepo.getToplistDetailFast(p, id)) {
                            is Result.Success -> {
                                val baseTracks = localRepo.applyFavoriteState(result.data)
                                _state.update {
                                    it.copy(
                                        tracks = baseTracks,
                                        editingTracks = emptyList(),
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false
                                    )
                                }

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
                                _state.update {
                                    it.copy(
                                        error = result.error.message,
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false
                                    )
                                }
                            }
                            is Result.Loading -> {}
                        }
                    }
                }
            }
        }
    }

    fun enterEditMode() {
        _state.update { current ->
            if (!current.isLocalPlaylist || current.isLoading) {
                current
            } else {
                current.copy(
                    isEditMode = true,
                    editingTracks = current.tracks,
                    error = null,
                    editMessage = null
                )
            }
        }
    }

    fun cancelEditMode() {
        _state.update { current ->
            current.copy(
                isEditMode = false,
                isSavingEdits = false,
                editingTracks = current.tracks,
                editMessage = null
            )
        }
    }

    fun removeEditingTrack(track: Track) {
        _state.update { current ->
            if (!current.isEditMode) {
                current
            } else {
                current.copy(
                    editingTracks = current.editingTracks.filterNot {
                        it.id == track.id && it.platform == track.platform
                    }
                )
            }
        }
    }

    fun moveEditingTrack(fromIndex: Int, toIndex: Int) {
        _state.update { current ->
            if (!current.isEditMode) return@update current
            val tracks = current.editingTracks
            if (
                fromIndex !in tracks.indices ||
                toIndex !in tracks.indices ||
                fromIndex == toIndex
            ) {
                return@update current
            }

            val reordered = tracks.toMutableList()
            val moved = reordered.removeAt(fromIndex)
            reordered.add(toIndex, moved)
            current.copy(editingTracks = reordered)
        }
    }

    fun commitPlaylistEdits() {
        val playlistId = currentPlaylistId ?: return
        val editingTracks = state.value.editingTracks
        if (!state.value.isLocalPlaylist || !state.value.isEditMode) return

        viewModelScope.launch {
            _state.update { it.copy(isSavingEdits = true, editMessage = null) }
            runCatching {
                localRepo.replacePlaylistSongs(playlistId, editingTracks)
            }.onSuccess {
                _state.update {
                    it.copy(
                        tracks = editingTracks,
                        editingTracks = editingTracks,
                        isEditMode = false,
                        isSavingEdits = false,
                        editMessage = null
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isSavingEdits = false,
                        editMessage = throwable.message ?: "保存歌单失败，请稍后再试。"
                    )
                }
            }
        }
    }
}
