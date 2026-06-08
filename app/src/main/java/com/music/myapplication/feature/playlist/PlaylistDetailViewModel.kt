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
    val sourceTracks: List<Track> = emptyList(),
    val editingTracks: List<Track> = emptyList(),
    val sortOrder: PlaylistTrackSort = PlaylistTrackSort.ORIGINAL,
    val isLoading: Boolean = false,
    val isSavingEdits: Boolean = false,
    val isDeletingFavorites: Boolean = false,
    val isLocalPlaylist: Boolean = false,
    val isFavoritesCollection: Boolean = false,
    val isEditMode: Boolean = false,
    val isFavoritesSelectionMode: Boolean = false,
    val selectedFavoriteKeys: Set<String> = emptySet(),
    val error: String? = null,
    val editMessage: String? = null,
    val title: String = "",
    val coverUrl: String = ""
)

enum class PlaylistTrackSort {
    ORIGINAL,
    TITLE,
    ARTIST,
    ALBUM,
    DURATION
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository
) : ViewModel() {

    private companion object {
        const val SOURCE_FAVORITES = "favorites"
        const val SOURCE_SMART = "smart"
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
                isDeletingFavorites = false,
                isLocalPlaylist = isLocalPlaylist,
                isFavoritesCollection = source == SOURCE_FAVORITES,
                isEditMode = false,
                isFavoritesSelectionMode = false,
                selectedFavoriteKeys = emptySet(),
                tracks = emptyList(),
                sourceTracks = emptyList(),
                sortOrder = PlaylistTrackSort.ORIGINAL,
                editingTracks = emptyList(),
                error = null,
                editMessage = null,
                title = title
            )
        }
        loadJob = viewModelScope.launch {
            if (source == SOURCE_FAVORITES) {
                localRepo.getFavorites().collect { tracks ->
                    val trackKeys = tracks.map(::favoriteTrackKey).toHashSet()
                    _state.update { current ->
                        val selectedKeys = current.selectedFavoriteKeys.filterTo(linkedSetOf()) { it in trackKeys }
                        val keepSelectionMode = current.isFavoritesSelectionMode && tracks.isNotEmpty()
                        current.copy(
                            title = title.ifBlank { "收藏" },
                            coverUrl = tracks.firstOrNull()?.coverUrl.orEmpty(),
                            sourceTracks = tracks,
                            tracks = tracks.sortedByPlaylist(current.sortOrder),
                            editingTracks = emptyList(),
                            isLoading = false,
                            isLocalPlaylist = false,
                            isFavoritesCollection = true,
                            isEditMode = false,
                            isFavoritesSelectionMode = keepSelectionMode,
                            selectedFavoriteKeys = selectedKeys
                        )
                    }
                }
            } else if (source == SOURCE_SMART) {
                localRepo.getSmartPlaylistTracks(id).collect { tracks ->
                    val trackKeys = tracks.map(::favoriteTrackKey).toHashSet()
                    _state.update { current ->
                        val selectedKeys = current.selectedFavoriteKeys.filterTo(linkedSetOf()) { it in trackKeys }
                        val keepSelectionMode = current.isFavoritesSelectionMode && tracks.isNotEmpty()
                        current.copy(
                            title = title.ifBlank { current.title.ifBlank { "智能歌单" } },
                            coverUrl = tracks.firstOrNull()?.coverUrl.orEmpty(),
                            sourceTracks = tracks,
                            tracks = tracks.sortedByPlaylist(current.sortOrder),
                            editingTracks = emptyList(),
                            isLoading = false,
                            isLocalPlaylist = false,
                            isFavoritesCollection = false,
                            isEditMode = false,
                            isFavoritesSelectionMode = keepSelectionMode,
                            selectedFavoriteKeys = selectedKeys,
                            isDeletingFavorites = false
                        )
                    }
                }
            } else if (platform == "local") {
                val playlist = localRepo.getPlaylistById(id)
                _state.update {
                    it.copy(
                        title = playlist?.name ?: title,
                        coverUrl = playlist?.coverUrl.orEmpty(),
                        isFavoritesSelectionMode = false,
                        selectedFavoriteKeys = emptySet(),
                        isDeletingFavorites = false
                    )
                }
                localRepo.getPlaylistSongs(id).collect { tracks ->
                    _state.update { current ->
                        val trackKeys = tracks.map(::favoriteTrackKey).toHashSet()
                        val selectedKeys = current.selectedFavoriteKeys.filterTo(linkedSetOf()) { it in trackKeys }
                        val keepSelectionMode = current.isFavoritesSelectionMode && tracks.isNotEmpty()
                        current.copy(
                            sourceTracks = tracks,
                            tracks = tracks.sortedByPlaylist(current.sortOrder),
                            editingTracks = if (current.isEditMode) current.editingTracks else tracks,
                            isLoading = false,
                            isLocalPlaylist = true,
                            isFavoritesCollection = false,
                            isFavoritesSelectionMode = keepSelectionMode,
                            selectedFavoriteKeys = selectedKeys,
                            isDeletingFavorites = false,
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
                                        sourceTracks = enriched,
                                        tracks = enriched.sortedByPlaylist(it.sortOrder),
                                        editingTracks = emptyList(),
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false,
                                        isFavoritesSelectionMode = false,
                                        selectedFavoriteKeys = emptySet(),
                                        isDeletingFavorites = false
                                    )
                                }
                            }
                            is Result.Error -> {
                                _state.update {
                                    it.copy(
                                        error = result.error.message,
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false,
                                        isFavoritesSelectionMode = false,
                                        selectedFavoriteKeys = emptySet(),
                                        isDeletingFavorites = false
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
                                        sourceTracks = baseTracks,
                                        tracks = baseTracks.sortedByPlaylist(it.sortOrder),
                                        editingTracks = emptyList(),
                                        isLoading = false,
                                        isLocalPlaylist = false,
                                        isFavoritesCollection = false,
                                        isFavoritesSelectionMode = false,
                                        selectedFavoriteKeys = emptySet(),
                                        isDeletingFavorites = false
                                    )
                                }

                                if (p == Platform.QQ || p == Platform.KUWO) {
                                    launch {
                                        val hydrated = onlineRepo.enrichToplistTracks(p, id, result.data)
                                        val hydratedTracks = localRepo.applyFavoriteState(hydrated)
                                        if (hydratedTracks != baseTracks) {
                                            _state.update {
                                                it.copy(
                                                    sourceTracks = hydratedTracks,
                                                    tracks = hydratedTracks.sortedByPlaylist(it.sortOrder)
                                                )
                                            }
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
                                        isFavoritesCollection = false,
                                        isFavoritesSelectionMode = false,
                                        selectedFavoriteKeys = emptySet(),
                                        isDeletingFavorites = false
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
                    isFavoritesSelectionMode = false,
                    selectedFavoriteKeys = emptySet(),
                    editingTracks = current.sourceTracks,
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
                editingTracks = current.sourceTracks,
                editMessage = null,
                isFavoritesSelectionMode = false,
                selectedFavoriteKeys = emptySet(),
                isDeletingFavorites = false
            )
        }
    }

    fun enterFavoritesSelectionMode() {
        _state.update { current ->
            if (!current.isFavoritesCollection || current.isLoading || current.tracks.isEmpty()) {
                current
            } else {
                current.copy(
                    isFavoritesSelectionMode = true,
                    isEditMode = false,
                    editingTracks = current.sourceTracks,
                    selectedFavoriteKeys = emptySet(),
                    isDeletingFavorites = false,
                    editMessage = null
                )
            }
        }
    }

    fun cancelFavoritesSelectionMode() {
        _state.update { current ->
            current.copy(
                isFavoritesSelectionMode = false,
                selectedFavoriteKeys = emptySet(),
                isDeletingFavorites = false,
                editMessage = null
            )
        }
    }

    fun toggleSelectAllFavorites() {
        _state.update { current ->
            if (!current.isFavoritesSelectionMode) return@update current
            val allKeys = current.tracks.map(::favoriteTrackKey).toSet()
            val shouldClear = current.selectedFavoriteKeys.size == allKeys.size
            current.copy(
                selectedFavoriteKeys = if (shouldClear) emptySet() else allKeys
            )
        }
    }

    fun toggleFavoriteSelection(track: Track) {
        _state.update { current ->
            if (!current.isFavoritesSelectionMode) return@update current
            val key = favoriteTrackKey(track)
            val selected = current.selectedFavoriteKeys.toMutableSet()
            if (!selected.add(key)) {
                selected.remove(key)
            }
            current.copy(selectedFavoriteKeys = selected)
        }
    }

    fun deleteSelectedFavorites() {
        val snapshot = state.value
        if (
            !snapshot.isFavoritesCollection ||
            !snapshot.isFavoritesSelectionMode ||
            snapshot.isDeletingFavorites ||
            snapshot.selectedFavoriteKeys.isEmpty()
        ) {
            return
        }
        val selectedTracks = snapshot.tracks.filter { favoriteTrackKey(it) in snapshot.selectedFavoriteKeys }
        if (selectedTracks.isEmpty()) {
            cancelFavoritesSelectionMode()
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isDeletingFavorites = true,
                    editMessage = null
                )
            }
            runCatching {
                selectedTracks.forEach { track ->
                    if (localRepo.isFavorite(track.id, track.platform.id)) {
                        localRepo.toggleFavorite(track)
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        isDeletingFavorites = false,
                        isFavoritesSelectionMode = false,
                        selectedFavoriteKeys = emptySet(),
                        editMessage = null
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isDeletingFavorites = false,
                        editMessage = throwable.message ?: "批量删除失败，请稍后再试。"
                    )
                }
            }
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
                        sourceTracks = editingTracks,
                        tracks = editingTracks.sortedByPlaylist(it.sortOrder),
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

    fun selectSort(sortOrder: PlaylistTrackSort) {
        _state.update { current ->
            if (current.sortOrder == sortOrder) {
                current
            } else {
                current.copy(
                    sortOrder = sortOrder,
                    tracks = current.sourceTracks.sortedByPlaylist(sortOrder),
                    isEditMode = false,
                    editingTracks = current.sourceTracks
                )
            }
        }
    }

    private fun favoriteTrackKey(track: Track): String = "${track.platform.id}:${track.id}"
}

private fun List<Track>.sortedByPlaylist(sortOrder: PlaylistTrackSort): List<Track> = when (sortOrder) {
    PlaylistTrackSort.ORIGINAL -> this
    PlaylistTrackSort.TITLE -> sortedWith(compareBy<Track> { it.title.normalizedSortKey() }
        .thenBy { it.artist.normalizedSortKey() })
    PlaylistTrackSort.ARTIST -> sortedWith(compareBy<Track> { it.artist.normalizedSortKey() }
        .thenBy { it.title.normalizedSortKey() })
    PlaylistTrackSort.ALBUM -> sortedWith(compareBy<Track> { it.album.blankLastSortKey() }
        .thenBy { it.title.normalizedSortKey() })
    PlaylistTrackSort.DURATION -> sortedWith(compareBy<Track> { if (it.durationMs > 0L) it.durationMs else Long.MAX_VALUE }
        .thenBy { it.title.normalizedSortKey() })
}

private fun String.normalizedSortKey(): String = trim().lowercase()

private fun String.blankLastSortKey(): String = trim()
    .ifBlank { "\uffff" }
    .lowercase()
