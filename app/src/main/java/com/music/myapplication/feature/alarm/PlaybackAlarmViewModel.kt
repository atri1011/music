package com.music.myapplication.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.myapplication.core.datastore.PlaybackAlarmSchedule
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.repository.LocalLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackAlarmUiState(
    val playlists: List<Playlist> = emptyList(),
    val scheduledAlarm: PlaybackAlarmSchedule? = null,
    val selectedPlaylistId: String = "",
    val hourText: String = "",
    val minuteText: String = "",
    val message: String? = null,
    val isSaving: Boolean = false
) {
    val selectedPlaylist: Playlist? get() = playlists.firstOrNull { it.id == selectedPlaylistId }
    val canSave: Boolean get() = selectedPlaylist != null && parsedHour() != null && parsedMinute() != null && !isSaving
}

private data class AlarmFormState(
    val selectedPlaylistId: String = "",
    val hourText: String = "",
    val minuteText: String = "",
    val initialized: Boolean = false,
    val message: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class PlaybackAlarmViewModel @Inject constructor(
    private val localRepository: LocalLibraryRepository,
    private val preferences: PlayerPreferences,
    private val scheduler: PlaybackAlarmScheduler
) : ViewModel() {

    private val formState = MutableStateFlow(AlarmFormState())

    val state: StateFlow<PlaybackAlarmUiState> = combine(
        localRepository.getPlaylists(),
        preferences.playbackAlarmSchedule,
        formState
    ) { playlists, alarm, form ->
        val defaults = defaultAlarmTime(alarm)
        val selectedPlaylistId = when {
            form.selectedPlaylistId.isNotBlank() -> form.selectedPlaylistId
            alarm?.playlistId in playlists.map { it.id } -> alarm?.playlistId.orEmpty()
            else -> playlists.firstOrNull()?.id.orEmpty()
        }
        PlaybackAlarmUiState(
            playlists = playlists,
            scheduledAlarm = alarm,
            selectedPlaylistId = selectedPlaylistId,
            hourText = if (form.initialized) form.hourText else defaults.first,
            minuteText = if (form.initialized) form.minuteText else defaults.second,
            message = form.message,
            isSaving = form.isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackAlarmUiState())

    fun selectPlaylist(playlistId: String) {
        formState.update { it.copy(selectedPlaylistId = playlistId, initialized = true, message = null) }
    }

    fun setHour(value: String) {
        formState.update { it.copy(hourText = value.onlyDigits(maxLength = 2), initialized = true, message = null) }
    }

    fun setMinute(value: String) {
        formState.update { it.copy(minuteText = value.onlyDigits(maxLength = 2), initialized = true, message = null) }
    }

    fun saveAlarm() {
        val current = state.value
        val playlist = current.selectedPlaylist ?: return publishMessage("请选择歌单")
        val hour = current.parsedHour() ?: return publishMessage("小时需为 0-23")
        val minute = current.parsedMinute() ?: return publishMessage("分钟需为 0-59")
        val triggerAt = nextTriggerAt(hour, minute)
        val schedule = PlaybackAlarmSchedule(
            playlistId = playlist.id,
            playlistName = playlist.name,
            triggerAtMs = triggerAt
        )
        viewModelScope.launch {
            formState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                preferences.savePlaybackAlarmSchedule(schedule)
                scheduler.schedule(schedule)
            }.onSuccess {
                formState.update {
                    it.copy(
                        selectedPlaylistId = playlist.id,
                        hourText = hour.toString().padStart(2, '0'),
                        minuteText = minute.toString().padStart(2, '0'),
                        initialized = true,
                        message = "已设置 ${formatTriggerTime(triggerAt)} 播放 ${playlist.name}",
                        isSaving = false
                    )
                }
            }.onFailure { error ->
                formState.update {
                    it.copy(
                        message = error.message ?: "设置闹钟失败",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun cancelAlarm() {
        viewModelScope.launch {
            scheduler.cancel()
            preferences.clearPlaybackAlarmSchedule()
            formState.update { it.copy(message = "已取消定时播放", initialized = true) }
        }
    }

    fun consumeMessage() {
        formState.update { it.copy(message = null) }
    }

    private fun publishMessage(message: String) {
        formState.update { it.copy(message = message) }
    }
}

fun PlaybackAlarmUiState.parsedHour(): Int? = hourText.toIntOrNull()?.takeIf { it in 0..23 }
fun PlaybackAlarmUiState.parsedMinute(): Int? = minuteText.toIntOrNull()?.takeIf { it in 0..59 }

fun nextTriggerAt(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis()): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMs) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return calendar.timeInMillis
}

fun formatTriggerTime(triggerAtMs: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = triggerAtMs }
    val dayLabel = if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    ) {
        "今天"
    } else {
        "明天"
    }
    return "$dayLabel ${target.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')}:" +
        target.get(Calendar.MINUTE).toString().padStart(2, '0')
}

private fun defaultAlarmTime(alarm: PlaybackAlarmSchedule?): Pair<String, String> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = alarm?.triggerAtMs?.takeIf { it > System.currentTimeMillis() }
            ?: (System.currentTimeMillis() + DEFAULT_ALARM_OFFSET_MS)
    }
    return calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0') to
        calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
}

private fun String.onlyDigits(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

private const val DEFAULT_ALARM_OFFSET_MS = 30L * 60L * 1000L
