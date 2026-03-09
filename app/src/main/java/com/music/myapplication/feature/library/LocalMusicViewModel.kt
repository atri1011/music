package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalMusicUiState(
    val tracks: List<Track> = emptyList(),
    val trackCount: Int = 0,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val statusMessageId: Long = 0L
)

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val localRepo: LocalLibraryRepository
) : ViewModel() {

    private val extras = MutableStateFlow(LocalMusicExtras())
    private var hasRequestedInitialSync = false
    private var nextMessageId = 0L

    val state: StateFlow<LocalMusicUiState> = combine(
        localRepo.getLocalTracks(),
        localRepo.getLocalTrackCount(),
        extras
    ) { tracks, count, currentExtras ->
        LocalMusicUiState(
            tracks = tracks,
            trackCount = count,
            isSyncing = currentExtras.isSyncing,
            statusMessage = currentExtras.statusMessage,
            statusMessageId = currentExtras.statusMessageId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalMusicUiState())

    fun syncLocalTracksIfNeeded() {
        if (hasRequestedInitialSync) return
        hasRequestedInitialSync = true
        syncLocalTracks()
    }

    fun syncLocalTracks() {
        if (extras.value.isSyncing) return

        viewModelScope.launch {
            extras.update { it.copy(isSyncing = true) }
            runCatching { localRepo.syncLocalTracks() }
                .onSuccess { count ->
                    publishMessage("已同步 $count 首本地歌曲")
                }
                .onFailure { error ->
                    publishMessage(error.message ?: "扫描本地音乐失败，请稍后重试")
                }
            extras.update { it.copy(isSyncing = false) }
        }
    }

    fun consumeStatusMessage() {
        extras.update { current ->
            if (current.statusMessage.isNullOrBlank()) current
            else current.copy(statusMessage = null)
        }
    }

    private fun publishMessage(message: String) {
        nextMessageId += 1L
        extras.update {
            it.copy(
                statusMessage = message,
                statusMessageId = nextMessageId
            )
        }
    }

    private data class LocalMusicExtras(
        val isSyncing: Boolean = false,
        val statusMessage: String? = null,
        val statusMessageId: Long = 0L
    )
}
