package com.music.myapplication.media.equalizer

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoudnessNormalizationManager @Inject constructor() {

    private var enhancer: LoudnessEnhancer? = null
    private var boundSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var targetGainMb: Int = DEFAULT_TARGET_GAIN_MB
    private var enabled: Boolean = true

    fun bindToAudioSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            release()
            return
        }
        if (audioSessionId == boundSessionId) {
            applySettings()
            return
        }

        releaseInternal()
        runCatching {
            LoudnessEnhancer(audioSessionId).also { effect ->
                enhancer = effect
                boundSessionId = audioSessionId
                applySettings()
            }
        }.onFailure {
            releaseInternal()
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        applySettings()
    }

    fun release() {
        releaseInternal()
    }

    private fun applySettings() {
        runCatching {
            enhancer?.setTargetGain(targetGainMb)
            enhancer?.enabled = enabled
        }
    }

    private fun releaseInternal() {
        runCatching { enhancer?.enabled = false }
        runCatching { enhancer?.release() }
        enhancer = null
        boundSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private companion object {
        const val DEFAULT_TARGET_GAIN_MB = 300
    }
}
