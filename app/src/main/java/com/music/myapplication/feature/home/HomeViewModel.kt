package com.music.myapplication.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.repository.OnlineMusicRepository
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val platform: Platform = Platform.NETEASE
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val onlineRepo: OnlineMusicRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadToplists()
    }

    fun loadToplists(platform: Platform = _state.value.platform) {
        _state.update { it.copy(isLoading = true, error = null, platform = platform) }
        viewModelScope.launch {
            when (val result = onlineRepo.getToplists(platform)) {
                is Result.Success -> {
                    _state.update { it.copy(toplists = result.data, isLoading = false) }
                }
                is Result.Error -> {
                    _state.update { it.copy(error = result.error.message, isLoading = false) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun onPlatformChange(platform: Platform) = loadToplists(platform)
}
