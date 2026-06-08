package com.music.myapplication.feature.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.feature.player.state.TrackPlaybackResolver
import com.music.myapplication.media.session.MediaControllerConnector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: PlayerPreferences
    @Inject lateinit var localRepository: LocalLibraryRepository
    @Inject lateinit var playbackResolver: TrackPlaybackResolver
    @Inject lateinit var controllerConnector: MediaControllerConnector

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlaybackAlarmScheduler.ACTION_PLAYBACK_ALARM) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                triggerScheduledPlayback(intent.getStringExtra(PlaybackAlarmScheduler.EXTRA_PLAYLIST_ID).orEmpty())
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun triggerScheduledPlayback(intentPlaylistId: String) {
        val schedule = preferences.playbackAlarmSchedule.first()
        val playlistId = schedule?.playlistId?.takeIf { it.isNotBlank() } ?: intentPlaylistId
        if (playlistId.isBlank()) {
            preferences.clearPlaybackAlarmSchedule()
            return
        }
        val tracks = localRepository.getPlaylistSongs(playlistId).first()
        if (tracks.isEmpty()) {
            preferences.clearPlaybackAlarmSchedule()
            return
        }

        val quality = preferences.quality.first()
        val resolved = when (val result = playbackResolver.resolve(tracks.first(), quality)) {
            is Result.Success -> result.data.track
            is Result.Error,
            Result.Loading -> null
        }
        if (resolved != null) {
            val queue = tracks.toMutableList().apply { this[0] = resolved }
            controllerConnector.connect()
            controllerConnector.playTrack(resolved, queue, index = 0)
            localRepository.recordRecentPlay(resolved)
        }
        preferences.clearPlaybackAlarmSchedule()
    }
}
