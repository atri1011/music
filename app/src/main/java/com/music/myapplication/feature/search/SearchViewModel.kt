package com.music.myapplication.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.datastore.SearchHistoryStore
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchSuggestion
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val hasMore: Boolean = true,
    val hotKeywords: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val showSuggestions: Boolean = false,
    val isHotLoading: Boolean = false,
    val isSuggestionLoading: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val localRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences,
    private val historyStore: SearchHistoryStore
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val hotKeywordsCache = mutableMapOf<Platform, List<String>>()
    private val searchQueryFlow = MutableStateFlow("")
    private val suggestionQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var hasUserSelectedPlatform = false

    init {
        viewModelScope.launch {
            val savedPlatform = Platform.fromId(preferences.platform.first())
            _state.update { s ->
                if (hasUserSelectedPlatform || s.platform == savedPlatform) s
                else s.copy(platform = savedPlatform)
            }
            loadHotKeywords(savedPlatform)
        }

        viewModelScope.launch {
            historyStore.history.collect { list ->
                _state.update { it.copy(searchHistory = list) }
            }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { query -> performSearch(query, _state.value.platform, 1) }
        }

        viewModelScope.launch {
            suggestionQueryFlow
                .debounce(150)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _state.update { it.copy(suggestions = emptyList(), showSuggestions = false) }
                        return@collectLatest
                    }
                    loadSuggestions(query, _state.value.platform)
                }
        }
    }

    fun onQueryChange(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
        }
        _state.update {
            if (query.isBlank()) {
                it.copy(
                    query = query, tracks = emptyList(), error = null,
                    page = 1, hasMore = true, isLoading = false,
                    suggestions = emptyList(), showSuggestions = false
                )
            } else {
                it.copy(query = query, error = null, showSuggestions = true)
            }
        }
        suggestionQueryFlow.value = query
        searchQueryFlow.value = query
    }

    fun submitSearch() {
        val s = _state.value
        if (s.query.isNotBlank()) {
            searchQueryFlow.value = ""
            suggestionQueryFlow.value = ""
            _state.update { it.copy(showSuggestions = false) }
            viewModelScope.launch { historyStore.record(s.query) }
            performSearch(s.query, s.platform, 1)
        }
    }

    fun onHistoryClick(keyword: String) {
        _state.update { it.copy(query = keyword, showSuggestions = false) }
        searchQueryFlow.value = ""
        suggestionQueryFlow.value = ""
        viewModelScope.launch { historyStore.record(keyword) }
        performSearch(keyword, _state.value.platform, 1)
    }

    fun onSuggestionClick(suggestion: SearchSuggestion) {
        _state.update { it.copy(query = suggestion.text, showSuggestions = false) }
        searchQueryFlow.value = ""
        suggestionQueryFlow.value = ""
        viewModelScope.launch { historyStore.record(suggestion.text) }
        performSearch(suggestion.text, _state.value.platform, 1)
    }

    fun onHotKeywordClick(keyword: String) {
        _state.update { it.copy(query = keyword, showSuggestions = false) }
        searchQueryFlow.value = ""
        suggestionQueryFlow.value = ""
        viewModelScope.launch { historyStore.record(keyword) }
        performSearch(keyword, _state.value.platform, 1)
    }

    fun dismissSuggestions() {
        _state.update { it.copy(showSuggestions = false) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    fun removeHistoryItem(keyword: String) {
        viewModelScope.launch { historyStore.remove(keyword) }
    }

    fun onPlatformChange(platform: Platform) {
        hasUserSelectedPlatform = true
        val currentState = _state.value
        if (currentState.platform == platform) {
            viewModelScope.launch { preferences.setPlatform(platform.id) }
            return
        }

        val cachedHotKeywords = hotKeywordsCache[platform].orEmpty()
        searchQueryFlow.value = ""
        suggestionQueryFlow.value = ""
        _state.update {
            it.copy(
                platform = platform, tracks = emptyList(), error = null,
                page = 1, hasMore = true,
                suggestions = emptyList(), showSuggestions = false,
                hotKeywords = cachedHotKeywords, isHotLoading = false
            )
        }
        viewModelScope.launch { preferences.setPlatform(platform.id) }

        if (currentState.query.isBlank()) {
            viewModelScope.launch { loadHotKeywords(platform) }
        } else {
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
                    isLoading = true, error = null,
                    tracks = if (page == 1) emptyList() else s.tracks
                )
            }
            when (val result = onlineRepo.search(platform, query, page)) {
                is Result.Success -> {
                    val enriched = localRepo.applyFavoriteState(result.data)
                    _state.update { s ->
                        if (s.platform != platform || s.query.trim() != query.trim()) return@update s
                        s.copy(
                            tracks = if (page == 1) enriched else s.tracks + enriched,
                            isLoading = false, page = page,
                            hasMore = enriched.size >= 20, error = null
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { s ->
                        if (s.platform != platform || s.query.trim() != query.trim()) return@update s
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

    private suspend fun loadHotKeywords(platform: Platform) {
        _state.update { state ->
            if (state.platform != platform) state
            else state.copy(isHotLoading = true)
        }
        when (val result = onlineRepo.getHotSearchKeywords(platform)) {
            is Result.Success -> {
                if (result.data.isNotEmpty()) {
                    hotKeywordsCache[platform] = result.data
                }
                _state.update { s ->
                    if (s.platform != platform) return@update s
                    s.copy(
                        hotKeywords = result.data.ifEmpty { hotKeywordsCache[platform].orEmpty() },
                        isHotLoading = false
                    )
                }
            }
            else -> _state.update { s ->
                if (s.platform != platform) return@update s
                s.copy(
                    hotKeywords = hotKeywordsCache[platform] ?: s.hotKeywords,
                    isHotLoading = false
                )
            }
        }
    }

    private suspend fun loadSuggestions(keyword: String, platform: Platform) {
        _state.update { it.copy(isSuggestionLoading = true) }
        when (val result = onlineRepo.getSearchSuggestions(platform, keyword)) {
            is Result.Success -> {
                _state.update { s ->
                    if (s.platform != platform || s.query.trim() != keyword.trim()) return@update s
                    s.copy(
                        suggestions = result.data,
                        showSuggestions = result.data.isNotEmpty(),
                        isSuggestionLoading = false
                    )
                }
            }
            else -> _state.update { it.copy(isSuggestionLoading = false) }
        }
    }

    fun retry() {
        val s = _state.value
        if (s.query.isNotBlank()) performSearch(s.query, s.platform, 1)
    }
}
