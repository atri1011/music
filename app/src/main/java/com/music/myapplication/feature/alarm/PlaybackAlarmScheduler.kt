package com.music.myapplication.feature.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.music.myapplication.MainActivity
import com.music.myapplication.core.datastore.PlaybackAlarmSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(schedule: PlaybackAlarmSchedule) {
        val triggerIntent = playbackAlarmIntent(schedule.playlistId)
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PLAYBACK_ALARM,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_PLAYBACK_ALARM,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(schedule.triggerAtMs, showIntent),
            operation
        )
    }

    fun cancel() {
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PLAYBACK_ALARM,
            playbackAlarmIntent(playlistId = ""),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun playbackAlarmIntent(playlistId: String): Intent =
        Intent(context, PlaybackAlarmReceiver::class.java).apply {
            action = ACTION_PLAYBACK_ALARM
            putExtra(EXTRA_PLAYLIST_ID, playlistId)
        }

    companion object {
        private const val REQUEST_CODE_PLAYBACK_ALARM = 43029
        const val ACTION_PLAYBACK_ALARM = "com.music.myapplication.action.PLAYBACK_ALARM"
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }
}
