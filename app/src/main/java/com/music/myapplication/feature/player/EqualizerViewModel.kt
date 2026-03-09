package com.music.myapplication.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.datastore.EqualizerPreferences
import com.music.myapplication.media.equalizer.EqualizerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqualizerUiState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val presets: List<String> = emptyList(),
    val selectedPresetIndex: Int = 0,
    val bands: List<BandUiState> = emptyList(),
    val bandLevelRange: IntRange = -1500..1500
)

data class BandUiState(
    val index: Int,
    val centerFreq: Int,
    val currentLevel: Int
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val equalizerPreferences: EqualizerPreferences
) : ViewModel() {

    private val editingBands = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private var persistBandsJob: Job? = null

    val uiState: StateFlow<EqualizerUiState> = combine(
        equalizerManager.state,
        equalizerPreferences.enabled,
        equalizerPreferences.presetIndex,
        equalizerPreferences.customBandLevels,
        editingBands
    ) { engine, enabled, presetIndex, storedBands, editing ->
        val effectiveBands = editing.takeIf { it.isNotEmpty() } ?: storedBands
        val bands = engine.bandFrequencies.mapIndexed { index, freq ->
            BandUiState(
                index = index,
                centerFreq = freq,
                currentLevel = effectiveBands[index]
                    ?: engine.bandLevels.getOrElse(index) { 0 }
            )
        }
        EqualizerUiState(
            isSupported = engine.isSupported,
            isEnabled = enabled,
            presets = engine.presetNames,
            selectedPresetIndex = if (editing.isNotEmpty()) -1 else presetIndex,
            bands = bands,
            bandLevelRange = engine.bandLevelRange
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EqualizerUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { equalizerPreferences.setEnabled(enabled) }
    }

    fun selectPreset(index: Int) {
        persistBandsJob?.cancel()
        editingBands.value = emptyMap()
        viewModelScope.launch {
            equalizerPreferences.setPresetIndex(index)
            equalizerPreferences.setCustomBandLevels(emptyMap())
        }
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        val state = uiState.value
        val clamped = level.coerceIn(state.bandLevelRange.first, state.bandLevelRange.last)
        val base = editingBands.value.takeIf { it.isNotEmpty() }
            ?: state.bands.associate { it.index to it.currentLevel }
        val updated = base.toMutableMap().apply { this[bandIndex] = clamped }

        editingBands.value = updated
        persistBandsJob?.cancel()
        persistBandsJob = viewModelScope.launch {
            delay(150)
            equalizerPreferences.setPresetIndex(-1)
            equalizerPreferences.setCustomBandLevels(updated)
            editingBands.update { current ->
                if (current == updated) emptyMap() else current
            }
        }
    }
}
