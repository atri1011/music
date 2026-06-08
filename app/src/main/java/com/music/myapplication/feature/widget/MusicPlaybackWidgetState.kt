package com.music.myapplication.feature.widget

import android.content.Context
import com.music.myapplication.domain.model.PlaybackState

data class MusicPlaybackWidgetSnapshot(
    val title: String = "Music Player",
    val artist: String = "打开应用开始播放",
    val isPlaying: Boolean = false,
    val hasTrack: Boolean = false
) {
    companion object {
        fun from(playbackState: PlaybackState): MusicPlaybackWidgetSnapshot {
            val track = playbackState.currentTrack ?: return MusicPlaybackWidgetSnapshot()
            return MusicPlaybackWidgetSnapshot(
                title = track.title.ifBlank { "未知歌曲" },
                artist = track.artist.ifBlank { "未知歌手" },
                isPlaying = playbackState.isPlaying,
                hasTrack = true
            )
        }
    }
}

object MusicPlaybackWidgetStateStore {
    private const val PREFS_NAME = "music_playback_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_HAS_TRACK = "has_track"

    fun save(context: Context, snapshot: MusicPlaybackWidgetSnapshot) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_HAS_TRACK, snapshot.hasTrack)
            .apply()
    }

    fun read(context: Context): MusicPlaybackWidgetSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return MusicPlaybackWidgetSnapshot(
            title = prefs.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() } ?: "Music Player",
            artist = prefs.getString(KEY_ARTIST, null)?.takeIf { it.isNotBlank() } ?: "打开应用开始播放",
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            hasTrack = prefs.getBoolean(KEY_HAS_TRACK, false)
        )
    }
}
