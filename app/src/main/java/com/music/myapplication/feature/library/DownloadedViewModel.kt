package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import com.music.myapplication.core.database.mapper.toTrack
import com.music.myapplication.core.download.DownloadManager
import com.music.myapplication.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadedUiTrack(
    val track: Track,
    val progressPercent: Int = 0,
    val failureReason: String = ""
)

data class DownloadedUiState(
    val downloading: List<DownloadedUiTrack> = emptyList(),
    val failed: List<DownloadedUiTrack> = emptyList(),
    val downloaded: List<DownloadedUiTrack> = emptyList()
) {
    val downloadedTracks: List<Track>
        get() = downloaded.map(DownloadedUiTrack::track)
}

@HiltViewModel
class DownloadedViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            downloadManager.reconcileTrackedDownloads()
        }
    }

    val state: StateFlow<DownloadedUiState> = downloadManager.getAllTracks()
        .map(::buildDownloadedUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadedUiState())

    fun removeDownloaded(track: Track) {
        viewModelScope.launch {
            downloadManager.removeDownloaded(track.id, track.platform.id)
        }
    }

    fun cancelDownload(track: Track) {
        viewModelScope.launch {
            downloadManager.cancelDownload(track.id, track.platform.id)
        }
    }
}

internal fun buildDownloadedUiState(entities: List<DownloadedTrackEntity>): DownloadedUiState {
    fun DownloadedTrackEntity.toUiTrack() = DownloadedUiTrack(
        track = toTrack(),
        progressPercent = progressPercent.coerceIn(0, 100),
        failureReason = failureReason
    )

    return DownloadedUiState(
        downloading = entities
            .filter { it.downloadStatus == DownloadedTrackEntity.DownloadStatus.DOWNLOADING }
            .map { it.toUiTrack() },
        failed = entities
            .filter { it.downloadStatus == DownloadedTrackEntity.DownloadStatus.FAILED }
            .map { it.toUiTrack() },
        downloaded = entities
            .filter { it.downloadStatus == DownloadedTrackEntity.DownloadStatus.SUCCESS }
            .map { it.toUiTrack() }
    )
}
