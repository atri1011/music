package com.music.myapplication.media.video

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoPlayerManager(context: Context) {
    private val appContext = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = true
    }

    fun prepare(playUrl: String) {
        if (playUrl.isBlank()) {
            clear()
            return
        }
        player.setMediaItem(MediaItem.fromUri(playUrl))
        player.prepare()
        player.playWhenReady = true
    }

    fun clear() {
        player.pause()
        player.clearMediaItems()
    }

    fun release() {
        player.release()
    }
}
