package com.music.myapplication.feature.library

import androidx.lifecycle.ViewModel
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.PlaybackEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class MusicYearReportUiState(
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val totalPlayCount: Int = 0,
    val totalListenDurationMs: Long = 0L,
    val activeDays: Int = 0,
    val activeHour: Int? = null,
    val topTracks: List<YearReportTrackStat> = emptyList(),
    val topArtists: List<YearReportArtistStat> = emptyList(),
    val calendarDays: List<YearReportDayStat> = emptyList()
) {
    val hasData: Boolean = totalPlayCount > 0 || topTracks.isNotEmpty() || topArtists.isNotEmpty()
}

data class YearReportTrackStat(
    val track: Track,
    val playCount: Int,
    val listenDurationMs: Long
)

data class YearReportArtistStat(
    val name: String,
    val playCount: Int,
    val listenDurationMs: Long
)

data class YearReportDayStat(
    val dayOfYear: Int,
    val playCount: Int,
    val listenDurationMs: Long
)

@HiltViewModel
class MusicYearReportViewModel @Inject constructor(
    localRepo: LocalLibraryRepository
) : ViewModel() {

    val state: StateFlow<MusicYearReportUiState> = localRepo.getPlaybackEvents(limit = 5000)
        .map { events -> buildMusicYearReport(events) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicYearReportUiState())
}

private fun buildMusicYearReport(events: List<PlaybackEvent>): MusicYearReportUiState {
    val now = Calendar.getInstance()
    val year = now.get(Calendar.YEAR)
    val yearEvents = events.filter { event ->
        Calendar.getInstance().apply { timeInMillis = event.playedAt }.get(Calendar.YEAR) == year
    }
    if (yearEvents.isEmpty()) return MusicYearReportUiState(year = year)

    val totalPlayCount = yearEvents.sumOf { it.playCount.coerceAtLeast(1) }
    val totalDuration = yearEvents.sumOf { it.effectiveListenDurationMs() }
    val topTracks = yearEvents
        .groupBy { "${it.track.platform.id}:${it.track.id}" }
        .map { (_, plays) ->
            YearReportTrackStat(
                track = plays.first().track,
                playCount = plays.sumOf { it.playCount.coerceAtLeast(1) },
                listenDurationMs = plays.sumOf { it.effectiveListenDurationMs() }
            )
        }
        .sortedWith(compareByDescending<YearReportTrackStat> { it.playCount }.thenByDescending { it.listenDurationMs })
        .take(5)
    val topArtists = yearEvents
        .groupBy { it.track.artist.ifBlank { "未知歌手" } }
        .map { (artist, plays) ->
            YearReportArtistStat(
                name = artist,
                playCount = plays.sumOf { it.playCount.coerceAtLeast(1) },
                listenDurationMs = plays.sumOf { it.effectiveListenDurationMs() }
            )
        }
        .sortedWith(compareByDescending<YearReportArtistStat> { it.playCount }.thenByDescending { it.listenDurationMs })
        .take(5)
    val calendarDays = yearEvents
        .groupBy { event -> Calendar.getInstance().apply { timeInMillis = event.playedAt }.get(Calendar.DAY_OF_YEAR) }
        .map { (day, plays) ->
            YearReportDayStat(
                dayOfYear = day,
                playCount = plays.sumOf { it.playCount.coerceAtLeast(1) },
                listenDurationMs = plays.sumOf { it.effectiveListenDurationMs() }
            )
        }
        .sortedBy { it.dayOfYear }
    val activeHour = yearEvents
        .groupBy { event -> Calendar.getInstance().apply { timeInMillis = event.playedAt }.get(Calendar.HOUR_OF_DAY) }
        .maxByOrNull { (_, plays) -> plays.sumOf { it.playCount.coerceAtLeast(1) } }
        ?.key

    return MusicYearReportUiState(
        year = year,
        totalPlayCount = totalPlayCount,
        totalListenDurationMs = totalDuration,
        activeDays = calendarDays.size,
        activeHour = activeHour,
        topTracks = topTracks,
        topArtists = topArtists,
        calendarDays = calendarDays
    )
}

private fun PlaybackEvent.effectiveListenDurationMs(): Long =
    listenDurationMs.takeIf { it > 0L } ?: (track.durationMs.coerceAtLeast(0L) * playCount.coerceAtLeast(1))
