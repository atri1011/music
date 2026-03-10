package com.music.myapplication.feature.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.AppError
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

data class AlbumDetailUiState(
    val albumName: String = "",
    val artistName: String = "",
    val coverUrl: String = "",
    val publishTime: String = "",
    val description: String = "",
    val company: String = "",
    val genre: String = "",
    val language: String = "",
    val subType: String = "",
    val tags: List<String> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineRepo: OnlineMusicRepository
) : ViewModel() {

    private val albumId: String = savedStateHandle["albumId"] ?: ""
    private val platformId: String = savedStateHandle["platform"] ?: "netease"
    private val albumName: String = savedStateHandle["albumName"] ?: ""
    private val artistName: String = savedStateHandle["artistName"] ?: ""
    private val coverUrl: String = savedStateHandle["coverUrl"] ?: ""
    private val platform = Platform.fromId(platformId)

    private val _state = MutableStateFlow(
        AlbumDetailUiState(
            albumName = albumName,
            artistName = artistName,
            coverUrl = coverUrl
        )
    )
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    init {
        loadAlbumDetail()
    }

    private fun loadAlbumDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (
                val result = onlineRepo.getAlbumDetailFull(
                    platform = platform,
                    albumId = albumId,
                    albumNameHint = albumName,
                    artistNameHint = artistName,
                    coverUrlHint = coverUrl
                )
            ) {
                is Result.Success -> {
                    val data = result.data
                    val info = data.info
                    _state.update {
                        it.copy(
                            tracks = data.tracks,
                            isLoading = false,
                            albumName = info.name.takeIf(String::isNotBlank) ?: it.albumName,
                            artistName = info.artistName.takeIf(String::isNotBlank)
                                ?: it.artistName,
                            coverUrl = info.coverUrl.takeIf(String::isNotBlank) ?: it.coverUrl,
                            publishTime = info.publishTime,
                            description = info.description,
                            company = info.company,
                            genre = info.genre,
                            language = info.language,
                            subType = info.subType,
                            tags = info.tags
                        )
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = (result.error as? AppError)?.message ?: "加载专辑详情失败"
                        )
                    }
                }

                is Result.Loading -> {}
            }
        }
    }

    fun retry() {
        loadAlbumDetail()
    }
}
