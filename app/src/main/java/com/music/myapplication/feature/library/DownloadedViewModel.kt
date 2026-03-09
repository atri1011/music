package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import com.music.myapplication.core.database.mapper.toTrack
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadedUiState(
    val tracks: List<Track> = emptyList(),
    val downloadingCount: Int = 0
)

@HiltViewModel
class DownloadedViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val state: StateFlow<DownloadedUiState> = downloadManager.getAllTracks()
        .map { entities ->
            DownloadedUiState(
                tracks = entities
                    .filter { it.downloadStatus == DownloadedTrackEntity.DownloadStatus.SUCCESS }
                    .map { it.toTrack() },
                downloadingCount = entities.count {
                    it.downloadStatus == DownloadedTrackEntity.DownloadStatus.DOWNLOADING
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadedUiState())

    fun removeDownloaded(track: Track) {
        viewModelScope.launch {
            downloadManager.removeDownloaded(track.id, track.platform.id)
        }
    }
}
