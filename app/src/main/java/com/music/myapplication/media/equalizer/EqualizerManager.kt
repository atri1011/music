package com.music.myapplication.media.equalizer

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerEngineState(
    val isSupported: Boolean = false,
    val isBound: Boolean = false,
    val numberOfBands: Int = 0,
    val presetNames: List<String> = emptyList(),
    val bandFrequencies: List<Int> = emptyList(),
    val bandLevels: List<Int> = emptyList(),
    val bandLevelRange: IntRange = -1500..1500
)

@Singleton
class EqualizerManager @Inject constructor() {

    private val supported: Boolean = runCatching {
        AudioEffect.queryEffects()?.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER } ?: false
    }.getOrDefault(false)

    private val _state = MutableStateFlow(EqualizerEngineState(isSupported = supported))
    val state: StateFlow<EqualizerEngineState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var boundSessionId: Int = 0

    val isSupported: Boolean get() = supported

    fun bindToAudioSession(audioSessionId: Int) {
        if (!supported || audioSessionId <= 0 || audioSessionId == boundSessionId) return
        releaseInternal()
        runCatching {
            Equalizer(0, audioSessionId).also { eq ->
                boundSessionId = audioSessionId
                equalizer = eq
                refreshState()
            }
        }.onFailure {
            releaseInternal()
            _state.value = EqualizerEngineState(isSupported = supported)
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        refreshState()
    }

    fun setPreset(index: Int) {
        val eq = equalizer ?: return
        val count = eq.numberOfPresets.toInt()
        if (count <= 0) return
        val safeIndex = index.coerceIn(0, count - 1).toShort()
        runCatching { eq.usePreset(safeIndex) }
        refreshState()
    }

    fun setBandLevel(band: Int, level: Int) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        if (band !in 0 until bands) return
        val range = eq.bandLevelRange
        val safeLevel = level.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
        runCatching { eq.setBandLevel(band.toShort(), safeLevel) }
        refreshState()
    }

    fun setBandLevels(levels: Map<Int, Int>) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        levels.forEach { (band, level) ->
            if (band in 0 until bands) {
                val safeLevel = level.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                runCatching { eq.setBandLevel(band.toShort(), safeLevel) }
            }
        }
        refreshState()
    }

    fun release() {
        releaseInternal()
        _state.value = EqualizerEngineState(isSupported = supported)
    }

    private fun refreshState() {
        val eq = equalizer
        if (eq == null) {
            _state.value = EqualizerEngineState(isSupported = supported)
            return
        }
        val range = eq.bandLevelRange
        val numBands = eq.numberOfBands.toInt()
        _state.value = EqualizerEngineState(
            isSupported = true,
            isBound = true,
            numberOfBands = numBands,
            presetNames = List(eq.numberOfPresets.toInt()) { i ->
                eq.getPresetName(i.toShort()).toString()
            },
            bandFrequencies = List(numBands) { i ->
                eq.getCenterFreq(i.toShort()) / 1000
            },
            bandLevels = List(numBands) { i ->
                eq.getBandLevel(i.toShort()).toInt()
            },
            bandLevelRange = range[0].toInt()..range[1].toInt()
        )
    }

    private fun releaseInternal() {
        runCatching { equalizer?.release() }
        equalizer = null
        boundSessionId = 0
    }
}
