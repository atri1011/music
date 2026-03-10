package com.music.myapplication.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.ArtistAlbum
import com.music.myapplication.domain.model.ArtistDetail
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

data class ArtistDetailUiState(
    val artistId: String = "",
    val artistName: String = "",
    val platform: Platform = Platform.NETEASE,
    val avatarUrl: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val hotSongs: List<Track> = emptyList(),
    val albums: List<ArtistAlbum> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineRepo: OnlineMusicRepository
) : ViewModel() {

    private val artistOrSongId: String = savedStateHandle["artistId"] ?: ""
    private val platformId: String = savedStateHandle["platform"] ?: "netease"
    private val artistName: String = savedStateHandle["artistName"] ?: ""
    private val platform = Platform.fromId(platformId)

    private val _state = MutableStateFlow(
        ArtistDetailUiState(
            artistId = artistOrSongId,
            artistName = artistName,
            platform = platform
        )
    )
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    init {
        loadArtist()
    }

    private fun loadArtist() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val directResult = onlineRepo.getArtistDetail(artistOrSongId, platform)) {
                is Result.Success -> {
                    applyArtistDetail(directResult.data)
                    return@launch
                }

                else -> Unit
            }

            val dummyTrack = Track(id = artistOrSongId, platform = platform, title = "", artist = artistName)
            val resolvedId = when (val ref = onlineRepo.resolveArtistRef(dummyTrack)) {
                is Result.Success -> {
                    _state.update { it.copy(artistId = ref.data.id) }
                    ref.data.id
                }

                else -> {
                    _state.update { it.copy(isLoading = false, error = "无法解析歌手信息") }
                    return@launch
                }
            }

            when (val result = onlineRepo.getArtistDetail(resolvedId, platform)) {
                is Result.Success -> applyArtistDetail(result.data)
                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = (result.error as? AppError)?.message ?: "加载失败"
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun applyArtistDetail(detail: ArtistDetail) {
        _state.update {
            it.copy(
                artistId = detail.id.ifBlank { it.artistId },
                artistName = detail.name.ifBlank { artistName },
                avatarUrl = detail.avatarUrl,
                description = detail.description,
                tags = detail.tags,
                hotSongs = detail.hotSongs,
                albums = detail.albums,
                isLoading = false,
                error = null
            )
        }
    }

    fun retry() {
        loadArtist()
    }
}
