package com.music.myapplication.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.cache.CacheManager
import com.music.myapplication.core.cache.CacheUsage
import com.music.myapplication.core.datastore.DarkModeOption
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.AudioSource
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
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val audioSource: AudioSource = AudioSource.TUNEHUB,
    val jkapiKey: String = "",
    val showJkapiKeyDialog: Boolean = false,
    val imageCacheBytes: Long = 0L,
    val lyricsCacheBytes: Long = 0L,
    val templateCacheBytes: Long = 0L,
    val isCacheLoading: Boolean = false,
    val isClearingCache: Boolean = false
) {
    val totalCacheBytes: Long
        get() = imageCacheBytes + lyricsCacheBytes + templateCacheBytes
}

private data class CacheUiState(
    val usage: CacheUsage = CacheUsage(),
    val isLoading: Boolean = false,
    val isClearing: Boolean = false
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val preferences: PlayerPreferences,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val dialogVisible = MutableStateFlow(false)
    private val jkapiDialogVisible = MutableStateFlow(false)
    private val cacheUiState = MutableStateFlow(CacheUiState())

    val state: StateFlow<MoreUiState> = combine(
        preferences.apiKey,
        dialogVisible,
        preferences.darkMode,
        preferences.autoPlay,
        preferences.wifiOnly,
        preferences.cacheLimitMb,
        preferences.quality,
        preferences.playbackMode,
        preferences.audioSource,
        preferences.jkapiKey,
        jkapiDialogVisible,
        cacheUiState
    ) { values ->
        val cacheState = values[11] as CacheUiState
        MoreUiState(
            apiKey = values[0] as String,
            showApiKeyDialog = values[1] as Boolean,
            darkMode = values[2] as DarkModeOption,
            autoPlay = values[3] as Boolean,
            wifiOnly = values[4] as Boolean,
            cacheLimitMb = values[5] as Int,
            quality = values[6] as String,
            playbackMode = values[7] as PlaybackMode,
            audioSource = values[8] as AudioSource,
            jkapiKey = values[9] as String,
            showJkapiKeyDialog = values[10] as Boolean,
            imageCacheBytes = cacheState.usage.imageBytes,
            lyricsCacheBytes = cacheState.usage.lyricsBytes,
            templateCacheBytes = cacheState.usage.templateBytes,
            isCacheLoading = cacheState.isLoading,
            isClearingCache = cacheState.isClearing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoreUiState())

    init {
        refreshCacheUsage()
    }

    fun showApiKeyDialog(show: Boolean) {
        dialogVisible.update { show }
    }

    fun showJkapiKeyDialog(show: Boolean) {
        jkapiDialogVisible.update { show }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            preferences.setApiKey(apiKey)
            showApiKeyDialog(false)
        }
    }

    fun saveJkapiKey(key: String) {
        viewModelScope.launch {
            preferences.setJkapiKey(key)
            showJkapiKeyDialog(false)
        }
    }

    fun setAudioSource(source: AudioSource) {
        viewModelScope.launch { preferences.setAudioSource(source) }
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

    fun refreshCacheUsage() {
        if (cacheUiState.value.isClearing) return
        viewModelScope.launch {
            cacheUiState.update { it.copy(isLoading = true) }
            runCatching { cacheManager.getUsage() }
                .onSuccess { usage ->
                    cacheUiState.update {
                        it.copy(
                            usage = usage,
                            isLoading = false
                        )
                    }
                }
                .onFailure {
                    cacheUiState.update { current -> current.copy(isLoading = false) }
                }
        }
    }

    fun clearCache() {
        if (cacheUiState.value.isClearing) return
        viewModelScope.launch {
            cacheUiState.update { it.copy(isClearing = true, isLoading = true) }
            runCatching {
                cacheManager.clearAll()
                cacheManager.getUsage()
            }.onSuccess { usage ->
                cacheUiState.update {
                    it.copy(
                        usage = usage,
                        isLoading = false,
                        isClearing = false
                    )
                }
            }.onFailure {
                cacheUiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isClearing = false
                    )
                }
            }
        }
    }
}
