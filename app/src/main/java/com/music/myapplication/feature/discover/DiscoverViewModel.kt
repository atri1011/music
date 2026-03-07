package com.music.myapplication.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.ToplistInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_SCENE_ID = "commute"
private const val HOT_TOPLIST_LIMIT = 4
private const val HOT_PREVIEW_LIMIT = 3
private const val SIMILAR_TRACK_LIMIT = 6

data class DiscoverUiState(
    val selectedPlatform: Platform = Platform.NETEASE,
    val selectedSceneId: String = DEFAULT_SCENE_ID,
    val themePlaylists: List<ToplistInfo> = emptyList(),
    val similarSeedTrack: Track? = null,
    val similarTracks: List<Track> = emptyList(),
    val hotToplists: List<ToplistInfo> = emptyList(),
    val hotToplistPreviews: Map<String, List<Track>> = emptyMap(),
    val isThemeLoading: Boolean = false,
    val isSimilarLoading: Boolean = false,
    val isHotLoading: Boolean = false,
    val themeError: String? = null,
    val similarError: String? = null,
    val hotError: String? = null
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val recommendationRepo: RecommendationRepository,
    private val localRepo: LocalLibraryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        loadThemePlaylists()
        loadSimilarTracks()
        loadHotZone(_state.value.selectedPlatform)
    }

    fun onSceneChange(sceneId: String) {
        _state.update { it.copy(selectedSceneId = sceneId) }
    }

    fun onPlatformChange(platform: Platform) {
        if (_state.value.selectedPlatform == platform && _state.value.hotToplists.isNotEmpty()) return
        loadHotZone(platform)
    }

    private fun loadThemePlaylists() {
        _state.update { it.copy(isThemeLoading = true, themeError = null) }
        viewModelScope.launch {
            runCatching { recommendationRepo.getRecommendedPlaylists() }
                .onSuccess { playlists ->
                    _state.update {
                        it.copy(
                            themePlaylists = playlists,
                            isThemeLoading = false,
                            themeError = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            themePlaylists = emptyList(),
                            isThemeLoading = false,
                            themeError = throwable.message ?: "主题歌单加载失败"
                        )
                    }
                }
        }
    }

    private fun loadSimilarTracks() {
        _state.update { it.copy(isSimilarLoading = true, similarError = null) }
        viewModelScope.launch {
            runCatching {
                val seedTrack = localRepo.getRecentPlays(limit = 1).first().firstOrNull()
                    ?: localRepo.getFavorites().first().firstOrNull()
                    ?: recommendationRepo.getDailyRecommendedTracks(limit = 1).firstOrNull()

                if (seedTrack == null) {
                    seedTrack to emptyList()
                } else {
                    val similarTracks = recommendationRepo.getSimilarTracks(
                        track = seedTrack,
                        limit = SIMILAR_TRACK_LIMIT
                    )
                    val favoriteAwareTracks = localRepo.applyFavoriteState(similarTracks)
                    seedTrack to favoriteAwareTracks
                }
            }.onSuccess { (seedTrack, similarTracks) ->
                _state.update {
                    it.copy(
                        similarSeedTrack = seedTrack,
                        similarTracks = similarTracks,
                        isSimilarLoading = false,
                        similarError = null
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        similarSeedTrack = null,
                        similarTracks = emptyList(),
                        isSimilarLoading = false,
                        similarError = throwable.message ?: "相似推荐加载失败"
                    )
                }
            }
        }
    }

    private fun loadHotZone(platform: Platform) {
        _state.update {
            it.copy(
                selectedPlatform = platform,
                hotToplists = emptyList(),
                hotToplistPreviews = emptyMap(),
                isHotLoading = true,
                hotError = null
            )
        }

        viewModelScope.launch {
            when (val result = onlineRepo.getToplists(platform)) {
                is Result.Success -> {
                    val topLists = result.data.take(HOT_TOPLIST_LIMIT)
                    if (_state.value.selectedPlatform != platform) return@launch
                    _state.update {
                        it.copy(
                            hotToplists = topLists,
                            isHotLoading = false,
                            hotError = null
                        )
                    }
                    loadHotToplistPreviews(platform, topLists)
                }

                is Result.Error -> {
                    if (_state.value.selectedPlatform != platform) return@launch
                    _state.update {
                        it.copy(
                            hotToplists = emptyList(),
                            hotToplistPreviews = emptyMap(),
                            isHotLoading = false,
                            hotError = result.error.message
                        )
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun loadHotToplistPreviews(platform: Platform, topLists: List<ToplistInfo>) {
        topLists.forEach { topList ->
            viewModelScope.launch {
                when (val result = onlineRepo.getToplistDetailFast(platform, topList.id)) {
                    is Result.Success -> {
                        if (_state.value.selectedPlatform != platform) return@launch
                        _state.update {
                            it.copy(
                                hotToplistPreviews = it.hotToplistPreviews + (
                                    topList.id to result.data.take(HOT_PREVIEW_LIMIT)
                                )
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
