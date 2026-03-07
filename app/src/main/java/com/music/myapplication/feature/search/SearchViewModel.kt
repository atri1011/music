package com.music.myapplication.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val platform: Platform = Platform.NETEASE,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var hasUserSelectedPlatform = false

    init {
        viewModelScope.launch {
            val savedPlatform = Platform.fromId(preferences.platform.first())
            _state.update { currentState ->
                if (hasUserSelectedPlatform || currentState.platform == savedPlatform) {
                    currentState
                } else {
                    currentState.copy(platform = savedPlatform)
                }
            }
        }
        viewModelScope.launch {
            queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { query -> performSearch(query, _state.value.platform, 1) }
        }
    }

    fun onQueryChange(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
        }
        _state.update {
            if (query.isBlank()) {
                it.copy(query = query, tracks = emptyList(), error = null, page = 1, hasMore = true, isLoading = false)
            } else {
                it.copy(query = query, error = null)
            }
        }
        queryFlow.value = query
    }

    fun submitSearch() {
        val s = _state.value
        if (s.query.isNotBlank()) {
            performSearch(s.query, s.platform, 1)
        }
    }

    fun onPlatformChange(platform: Platform) {
        hasUserSelectedPlatform = true
        val currentState = _state.value
        if (currentState.platform == platform) {
            viewModelScope.launch { preferences.setPlatform(platform.id) }
            return
        }

        _state.update { it.copy(platform = platform, tracks = emptyList(), error = null, page = 1, hasMore = true) }
        viewModelScope.launch { preferences.setPlatform(platform.id) }
        if (currentState.query.isNotBlank()) {
            performSearch(currentState.query, platform, 1)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || !s.hasMore || s.query.isBlank()) return
        performSearch(s.query, s.platform, s.page + 1)
    }

    private fun performSearch(query: String, platform: Platform, page: Int) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { s ->
                s.copy(
                    isLoading = true,
                    error = null,
                    tracks = if (page == 1) emptyList() else s.tracks
                )
            }
            when (val result = onlineRepo.search(platform, query, page)) {
                is Result.Success -> {
                    val enriched = result.data.map { track ->
                        val isFav = localRepo.isFavorite(track.id, track.platform.id)
                        track.copy(isFavorite = isFav)
                    }
                    _state.update { s ->
                        s.copy(
                            tracks = if (page == 1) enriched else s.tracks + enriched,
                            isLoading = false,
                            page = page,
                            hasMore = enriched.size >= 20,
                            error = null
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { s ->
                        s.copy(
                            isLoading = false,
                            error = (result.error as AppError).message,
                            tracks = if (page == 1) emptyList() else s.tracks
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun retry() {
        val s = _state.value
        if (s.query.isNotBlank()) performSearch(s.query, s.platform, 1)
    }
}
