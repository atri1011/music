package com.music.myapplication.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.HomeContentCacheStore
import com.music.myapplication.core.datastore.HomeFirstPaintCache
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.NetworkMonitor
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.PlaylistCategory
import com.music.myapplication.domain.model.PlaylistPreview
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.RecentPlay
import com.music.myapplication.domain.repository.ToplistInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class HomeRecentArtist(
    val artistName: String,
    val seedTrackId: String,
    val platform: Platform,
    val coverUrl: String,
    val listenCount: Int
)

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
    val continueListeningEntries: List<RecentPlay> = emptyList(),
    val recentArtists: List<HomeRecentArtist> = emptyList(),
    // Playlist Square
    val playlistSquarePlatform: Platform = Platform.NETEASE,
    val playlistCategories: List<PlaylistCategory> = emptyList(),
    val selectedPlaylistCategory: String = "全部",
    val playlistItems: List<PlaylistPreview> = emptyList(),
    val isPlaylistSquareLoading: Boolean = false,
    val isPlaylistSquareRefreshing: Boolean = false,
    val isPlaylistSquareLoadingMore: Boolean = false,
    val playlistSquareError: String? = null,
    val playlistSquarePage: Int = 1,
    val playlistSquareHasMore: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val recommendationRepo: RecommendationRepository,
    private val localLibraryRepo: LocalLibraryRepository,
    private val preferences: PlayerPreferences,
    private val homeContentCacheStore: HomeContentCacheStore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var refreshCount = 0
    private var guessYouLikeJob: Job? = null
    private var playlistSquareJob: Job? = null
    private var playlistCategoryJob: Job? = null

    init {
        observeRecentListening()
        viewModelScope.launch {
            restoreFirstPaintCache()
            loadToplists()
            loadRecommendations()
        }
    }

    private fun observeRecentListening() {
        viewModelScope.launch {
            localLibraryRepo.getRecentPlayEntries(limit = 24).collect { entries ->
                _state.update {
                    it.copy(
                        continueListeningEntries = entries.take(5),
                        recentArtists = entries.toRecentArtists()
                    )
                }
            }
        }
    }

    fun loadToplists(platform: Platform = _state.value.platform) {
        _state.update { it.copy(isLoading = true, error = null, platform = platform, toplistPreviews = emptyMap()) }
        viewModelScope.launch {
            when (val result = onlineRepo.getToplists(platform)) {
                is Result.Success -> {
                    _state.update { it.copy(toplists = result.data, isLoading = false) }
                    cacheFirstPaintSnapshot()
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
                cacheFirstPaintSnapshot()
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val fmTrack = recommendationRepo.getFmTrack()
                _state.update { it.copy(fmTrack = fmTrack) }
                cacheFirstPaintSnapshot()
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                val playlists = recommendationRepo.getRecommendedPlaylists()
                _state.update { it.copy(recommendedPlaylists = playlists) }
                cacheFirstPaintSnapshot()
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
                        ).also { cacheFirstPaintSnapshot(it) }
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
            _state.update {
                it.copy(
                    isPlaylistSquareLoading = true,
                    isPlaylistSquareRefreshing = false,
                    isPlaylistSquareLoadingMore = false,
                    playlistSquareError = null
                )
            }
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
                        s.copy(
                            isPlaylistSquareLoading = false,
                            isPlaylistSquareRefreshing = false,
                            isPlaylistSquareLoadingMore = false,
                            playlistSquareError = result.error.message
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun loadPlaylistSquare(reset: Boolean = false, preserveCurrentItems: Boolean = false) {
        val s = _state.value
        if (!reset && s.isPlaylistSquareLoading) return
        val page = if (reset) 1 else s.playlistSquarePage
        val platform = s.playlistSquarePlatform
        val category = s.selectedPlaylistCategory
        _state.update {
            it.copy(
                isPlaylistSquareLoading = true,
                isPlaylistSquareRefreshing = reset && preserveCurrentItems && it.playlistItems.isNotEmpty(),
                isPlaylistSquareLoadingMore = !reset && it.playlistItems.isNotEmpty(),
                playlistSquareError = null,
                playlistItems = if (reset && !preserveCurrentItems) emptyList() else it.playlistItems
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
                            isPlaylistSquareRefreshing = false,
                            isPlaylistSquareLoadingMore = false,
                            playlistSquarePage = page + 1,
                            playlistSquareHasMore = result.data.size >= 30
                        )
                    }
                }
                is Result.Error -> {
                    _state.update { cur ->
                        if (cur.playlistSquarePlatform != platform || cur.selectedPlaylistCategory != category) return@update cur
                        cur.copy(
                            isPlaylistSquareLoading = false,
                            isPlaylistSquareRefreshing = false,
                            isPlaylistSquareLoadingMore = false,
                            playlistSquareError = result.error.message
                        )
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

    fun refreshPlaylistSquare() {
        if (_state.value.isPlaylistSquareLoading) return
        if (_state.value.playlistCategories.isEmpty()) {
            loadPlaylistCategories()
        } else {
            loadPlaylistSquare(reset = true, preserveCurrentItems = true)
        }
    }

    fun onPlaylistSquarePlatformChange(platform: Platform) {
        if (_state.value.playlistSquarePlatform == platform) return
        _state.update {
            it.copy(
                playlistSquarePlatform = platform,
                playlistCategories = emptyList(),
                playlistItems = emptyList(),
                isPlaylistSquareRefreshing = false,
                isPlaylistSquareLoadingMore = false,
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
                isPlaylistSquareRefreshing = false,
                isPlaylistSquareLoadingMore = false,
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

    private suspend fun restoreFirstPaintCache() {
        val cache = homeContentCacheStore.getCachedFirstPaint() ?: return
        _state.update { current ->
            current.copy(
                platform = cache.platform,
                toplists = cache.toplists,
                dailyTracks = cache.dailyTracks,
                fmTrack = cache.fmTrack,
                recommendedPlaylists = cache.recommendedPlaylists,
                guessYouLikeLabel = cache.guessYouLikeLabel,
                guessYouLikeTracks = cache.guessYouLikeTracks
            )
        }
    }

    private fun cacheFirstPaintSnapshot(state: HomeUiState = _state.value) {
        viewModelScope.launch {
            homeContentCacheStore.cacheFirstPaint(
                HomeFirstPaintCache(
                    platform = state.platform,
                    toplists = state.toplists,
                    dailyTracks = state.dailyTracks,
                    fmTrack = state.fmTrack,
                    recommendedPlaylists = state.recommendedPlaylists,
                    guessYouLikeLabel = state.guessYouLikeLabel,
                    guessYouLikeTracks = state.guessYouLikeTracks
                )
            )
        }
    }

    private fun List<RecentPlay>.toRecentArtists(): List<HomeRecentArtist> {
        val grouped = linkedMapOf<String, MutableList<RecentPlay>>()
        for (entry in this) {
            val artistName = entry.track.artist.trim()
            if (artistName.isBlank()) continue
            val key = "${entry.track.platform.id}:${artistName.lowercase(Locale.ROOT)}"
            grouped.getOrPut(key) { mutableListOf() }.add(entry)
        }

        return grouped.values.take(6).map { plays ->
            val seed = plays.first().track
            HomeRecentArtist(
                artistName = seed.artist,
                seedTrackId = seed.id,
                platform = seed.platform,
                coverUrl = seed.coverUrl,
                listenCount = plays.sumOf { it.playCount }.coerceAtLeast(plays.size)
            )
        }
    }
}
