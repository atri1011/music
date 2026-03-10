package com.music.myapplication.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.NetworkMonitor
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaylistCategory
import com.music.myapplication.domain.model.PlaylistPreview
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.ToplistInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val toplists: List<ToplistInfo> = emptyList(),
    val toplistPreviews: Map<String, List<Track>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val platform: Platform = Platform.NETEASE,
    val selectedTab: Int = 0,
    val dailyTracks: List<Track> = emptyList(),
    val fmTrack: Track? = null,
    val recommendedPlaylists: List<ToplistInfo> = emptyList(),
    val isRecommendationLoading: Boolean = false,
    val guessYouLikeLabel: String = "",
    val guessYouLikeTracks: List<Track> = emptyList(),
    val isGuessYouLikeLoading: Boolean = false,
    // Playlist Square
    val playlistSquarePlatform: Platform = Platform.NETEASE,
    val playlistCategories: List<PlaylistCategory> = emptyList(),
    val selectedPlaylistCategory: String = "全部",
    val playlistItems: List<PlaylistPreview> = emptyList(),
    val isPlaylistSquareLoading: Boolean = false,
    val playlistSquareError: String? = null,
    val playlistSquarePage: Int = 1,
    val playlistSquareHasMore: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val recommendationRepo: RecommendationRepository,
    private val preferences: PlayerPreferences,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var refreshCount = 0
    private var guessYouLikeJob: Job? = null
    private var playlistSquareJob: Job? = null
    private var playlistCategoryJob: Job? = null

    init {
        loadToplists()
        loadRecommendations()
    }

    fun loadToplists(platform: Platform = _state.value.platform) {
        _state.update { it.copy(isLoading = true, error = null, platform = platform, toplistPreviews = emptyMap()) }
        viewModelScope.launch {
            when (val result = onlineRepo.getToplists(platform)) {
                is Result.Success -> {
                    _state.update { it.copy(toplists = result.data, isLoading = false) }
                    loadToplistPreviews(result.data, platform)
                }
                is Result.Error -> {
                    _state.update { it.copy(error = result.error.message, isLoading = false) }
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun loadToplistPreviews(toplists: List<ToplistInfo>, platform: Platform) {
        toplists.forEach { toplist ->
            viewModelScope.launch {
                if (!shouldRunToplistPreviewPreload()) return@launch
                when (val result = onlineRepo.getToplistDetailFast(platform, toplist.id)) {
                    is Result.Success -> {
                        _state.update {
                            it.copy(toplistPreviews = it.toplistPreviews + (toplist.id to result.data.take(3)))
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun shouldRunToplistPreviewPreload(): Boolean {
        val wifiOnly = preferences.wifiOnly.first()
        return !wifiOnly || networkMonitor.isUnmeteredConnection()
    }

    fun loadRecommendations() {
        _state.update { it.copy(isRecommendationLoading = true) }
        viewModelScope.launch {
            try {
                val dailyTracks = recommendationRepo.getDailyRecommendedTracks()
                _state.update { it.copy(dailyTracks = dailyTracks) }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val fmTrack = recommendationRepo.getFmTrack()
                _state.update { it.copy(fmTrack = fmTrack) }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val playlists = recommendationRepo.getRecommendedPlaylists()
                _state.update { it.copy(recommendedPlaylists = playlists) }
            } catch (_: Exception) {
            } finally {
                _state.update { it.copy(isRecommendationLoading = false) }
            }
        }
        loadGuessYouLike()
    }

    fun refreshGuessYouLike() {
        loadGuessYouLike()
    }

    private fun loadGuessYouLike() {
        guessYouLikeJob?.cancel()
        refreshCount++
        _state.update { it.copy(isGuessYouLikeLoading = true) }
        guessYouLikeJob = viewModelScope.launch {
            try {
                val result = recommendationRepo.getGuessYouLikeTracks(refreshCount)
                _state.update { current ->
                    if (result.tracks.isNotEmpty()) {
                        current.copy(
                            guessYouLikeLabel = result.label.takeIf { it.isNotBlank() } ?: current.guessYouLikeLabel,
                            guessYouLikeTracks = result.tracks,
                            isGuessYouLikeLoading = false
                        )
                    } else {
                        current.copy(isGuessYouLikeLoading = false)
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(isGuessYouLikeLoading = false) }
            }
        }
    }

    fun onPlatformChange(platform: Platform) = loadToplists(platform)

    fun onTabChange(tab: Int) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == 2 && _state.value.playlistCategories.isEmpty()) {
            loadPlaylistCategories()
        }
    }

    // ── Playlist Square ──

    fun loadPlaylistCategories(platform: Platform = _state.value.playlistSquarePlatform) {
        playlistCategoryJob?.cancel()
        playlistCategoryJob = viewModelScope.launch {
            _state.update { it.copy(isPlaylistSquareLoading = true, playlistSquareError = null) }
            when (val result = onlineRepo.getPlaylistCategories(platform)) {
                is Result.Success -> {
                    _state.update { s ->
                        if (s.playlistSquarePlatform != platform) return@update s
                        s.copy(
                            playlistCategories = result.data,
                            selectedPlaylistCategory = result.data.firstOrNull()?.name ?: "全部"
                        )
                    }
                    loadPlaylistSquare(reset = true)
                }
                is Result.Error -> {
                    _state.update { s ->
                        if (s.playlistSquarePlatform != platform) return@update s
                        s.copy(isPlaylistSquareLoading = false, playlistSquareError = result.error.message)
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun loadPlaylistSquare(reset: Boolean = false) {
        val s = _state.value
        if (!reset && s.isPlaylistSquareLoading) return
        val page = if (reset) 1 else s.playlistSquarePage
        val platform = s.playlistSquarePlatform
        val category = s.selectedPlaylistCategory
        _state.update {
            it.copy(
                isPlaylistSquareLoading = true,
                playlistSquareError = null,
                playlistItems = if (reset) emptyList() else it.playlistItems
            )
        }
        playlistSquareJob?.cancel()
        playlistSquareJob = viewModelScope.launch {
            when (val result = onlineRepo.getPlaylistsByCategory(platform, category, page)) {
                is Result.Success -> {
                    _state.update { cur ->
                        if (cur.playlistSquarePlatform != platform || cur.selectedPlaylistCategory != category) return@update cur
                        cur.copy(
                            playlistItems = if (reset) result.data else cur.playlistItems + result.data,
                            isPlaylistSquareLoading = false,
                            playlistSquarePage = page + 1,
                            playlistSquareHasMore = result.data.size >= 30
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { cur ->
                        if (cur.playlistSquarePlatform != platform || cur.selectedPlaylistCategory != category) return@update cur
                        cur.copy(isPlaylistSquareLoading = false, playlistSquareError = result.error.message)
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun loadMorePlaylistSquare() {
        val s = _state.value
        if (s.isPlaylistSquareLoading || !s.playlistSquareHasMore) return
        loadPlaylistSquare(reset = false)
    }

    fun onPlaylistSquarePlatformChange(platform: Platform) {
        if (_state.value.playlistSquarePlatform == platform) return
        _state.update {
            it.copy(
                playlistSquarePlatform = platform,
                playlistCategories = emptyList(),
                playlistItems = emptyList(),
                playlistSquarePage = 1,
                playlistSquareHasMore = true
            )
        }
        loadPlaylistCategories(platform)
    }

    fun onPlaylistCategoryChange(category: String) {
        if (_state.value.selectedPlaylistCategory == category) return
        _state.update {
            it.copy(
                selectedPlaylistCategory = category,
                playlistItems = emptyList(),
                playlistSquarePage = 1,
                playlistSquareHasMore = true
            )
        }
        loadPlaylistSquare(reset = true)
    }

    fun retryPlaylistSquare() {
        if (_state.value.playlistCategories.isEmpty()) {
            loadPlaylistCategories()
        } else {
            loadPlaylistSquare(reset = true)
        }
    }
}
