package com.music.myapplication.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.datastore.PlayerPreferences
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
    val showApiKeyDialog: Boolean = false
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val preferences: PlayerPreferences
) : ViewModel() {

    private val dialogVisible = MutableStateFlow(false)

    val state: StateFlow<MoreUiState> = combine(
        preferences.apiKey,
        dialogVisible
    ) { apiKey, showDialog ->
        MoreUiState(
            apiKey = apiKey,
            showApiKeyDialog = showDialog
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
}
