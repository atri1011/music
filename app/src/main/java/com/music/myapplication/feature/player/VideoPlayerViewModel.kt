package com.music.myapplication.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.music.myapplication.app.navigation.Routes
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoPlayerUiState(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val platformId: String = "",
    val platformName: String = "",
    val playUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val hasPlayableVideo: Boolean
        get() = playUrl.isNotBlank()
}

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineRepo: OnlineMusicRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Routes.VideoPlayer>()
    private val track = Track(
        id = route.trackId,
        platform = Platform.fromId(route.platform),
        title = route.title,
        artist = route.artist,
        coverUrl = route.coverUrl
    )

    private val _state = MutableStateFlow(
        VideoPlayerUiState(
            title = route.title,
            artist = route.artist,
            coverUrl = route.coverUrl,
            platformId = route.platform,
            platformName = Platform.entries.firstOrNull { it.id == route.platform }?.displayName.orEmpty(),
            playUrl = route.playUrl
        )
    )
    val state: StateFlow<VideoPlayerUiState> = _state.asStateFlow()

    init {
        if (route.playUrl.isBlank()) {
            loadVideoUrl()
        }
    }

    fun retry() {
        loadVideoUrl()
    }

    private fun loadVideoUrl() {
        if (track.id.isBlank()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "缺少歌曲信息，没法加载 MV"
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    playUrl = ""
                )
            }

            when (val result = onlineRepo.resolveVideoUrl(track)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            playUrl = result.data,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.error.message
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }
}
