package com.music.myapplication.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
import com.music.myapplication.domain.repository.ToplistInfo
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isRecommendationLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository,
    private val recommendationRepo: RecommendationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

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

    fun loadRecommendations() {
        _state.update { it.copy(isRecommendationLoading = true) }
        viewModelScope.launch {
            val dailyTracks = recommendationRepo.getDailyRecommendedTracks()
            val fmTrack = recommendationRepo.getFmTrack()
            val playlists = recommendationRepo.getRecommendedPlaylists()
            _state.update {
                it.copy(
                    dailyTracks = dailyTracks,
                    fmTrack = fmTrack,
                    recommendedPlaylists = playlists,
                    isRecommendationLoading = false
                )
            }
        }
    }

    fun onPlatformChange(platform: Platform) = loadToplists(platform)

    fun onTabChange(tab: Int) {
        _state.update { it.copy(selectedTab = tab) }
    }
}
