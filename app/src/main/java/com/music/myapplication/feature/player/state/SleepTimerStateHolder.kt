package com.music.myapplication.feature.player.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SleepTimerMode {
    OFF,
    COUNTDOWN,
    AFTER_CURRENT_TRACK
}

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L
) {
    val isActive: Boolean get() = mode != SleepTimerMode.OFF
    val remainingMinutes: Int get() = ((remainingMs + 59_999) / 60_000).toInt()
}

@Singleton
class SleepTimerStateHolder @Inject constructor() {
    private lateinit var scope: CoroutineScope
    private var countdownJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    var onTimerExpired: (() -> Unit)? = null

    fun bind(scope: CoroutineScope) {
        this.scope = scope
    }

    fun startCountdown(minutes: Int) {
        cancel()
        val totalMs = minutes * 60_000L
        _state.update {
            SleepTimerState(
                mode = SleepTimerMode.COUNTDOWN,
                remainingMs = totalMs,
                totalMs = totalMs
            )
        }
        countdownJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                _state.update { it.copy(remainingMs = remaining.coerceAtLeast(0)) }
            }
            onTimerExpired?.invoke()
            _state.update { SleepTimerState() }
        }
    }

    fun startAfterCurrentTrack() {
        cancel()
        _state.update {
            SleepTimerState(mode = SleepTimerMode.AFTER_CURRENT_TRACK)
        }
    }

    fun shouldPauseAfterCurrentTrack(): Boolean =
        _state.value.mode == SleepTimerMode.AFTER_CURRENT_TRACK

    fun handleCurrentTrackEnded() {
        if (!shouldPauseAfterCurrentTrack()) return
        onTimerExpired?.invoke()
        _state.update { SleepTimerState() }
    }

    fun cancel() {
        countdownJob?.cancel()
        countdownJob = null
        _state.update { SleepTimerState() }
    }
}
