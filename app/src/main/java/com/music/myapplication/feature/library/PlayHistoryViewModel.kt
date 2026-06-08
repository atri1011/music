package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.RecentPlay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class PlayHistoryUiState(
    val entries: List<RecentPlay> = emptyList()
)

@HiltViewModel
class PlayHistoryViewModel @Inject constructor(
    localRepo: LocalLibraryRepository
) : ViewModel() {
    val state: StateFlow<PlayHistoryUiState> = localRepo.getRecentPlayEntries(limit = 100)
        .map { PlayHistoryUiState(entries = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayHistoryUiState())
}
