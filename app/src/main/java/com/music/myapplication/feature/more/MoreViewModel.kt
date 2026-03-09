package com.music.myapplication.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.datastore.DarkModeOption
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlaybackMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoreUiState(
    val apiKey: String = "",
    val showApiKeyDialog: Boolean = false,
    val darkMode: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM,
    val autoPlay: Boolean = true,
    val wifiOnly: Boolean = false,
    val cacheLimitMb: Int = 500,
    val quality: String = "128k",
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val preferences: PlayerPreferences
) : ViewModel() {

    private val dialogVisible = MutableStateFlow(false)

    val state: StateFlow<MoreUiState> = combine(
        preferences.apiKey,
        dialogVisible,
        preferences.darkMode,
        preferences.autoPlay,
        preferences.wifiOnly,
        preferences.cacheLimitMb,
        preferences.quality,
        preferences.playbackMode
    ) { values ->
        MoreUiState(
            apiKey = values[0] as String,
            showApiKeyDialog = values[1] as Boolean,
            darkMode = values[2] as DarkModeOption,
            autoPlay = values[3] as Boolean,
            wifiOnly = values[4] as Boolean,
            cacheLimitMb = values[5] as Int,
            quality = values[6] as String,
            playbackMode = values[7] as PlaybackMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoreUiState())

    fun showApiKeyDialog(show: Boolean) {
        dialogVisible.update { show }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            preferences.setApiKey(apiKey)
            showApiKeyDialog(false)
        }
    }

    fun setDarkMode(option: DarkModeOption) {
        viewModelScope.launch { preferences.setDarkMode(option) }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoPlay(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { preferences.setWifiOnly(enabled) }
    }

    fun setCacheLimitMb(limitMb: Int) {
        viewModelScope.launch { preferences.setCacheLimitMb(limitMb) }
    }

    fun setQuality(quality: String) {
        viewModelScope.launch { preferences.setQuality(quality) }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        viewModelScope.launch { preferences.setPlaybackMode(mode) }
    }
}
