package com.music.myapplication.media.session

import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val LOAD_TRACK_CUSTOM_ACTION = "com.music.myapplication.media.LOAD_TRACK"
private const val REFRESH_QUEUE_CUSTOM_ACTION = "com.music.myapplication.media.REFRESH_QUEUE"
private const val EXTRA_REQUEST_PAYLOAD = "request_payload"

private val playbackLoadJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal val loadTrackSessionCommand = SessionCommand(LOAD_TRACK_CUSTOM_ACTION, Bundle.EMPTY)
internal val refreshQueueSessionCommand = SessionCommand(REFRESH_QUEUE_CUSTOM_ACTION, Bundle.EMPTY)

@Serializable
internal data class PlaybackLoadRequest(
    val track: Track,
    val queue: List<Track>,
    val index: Int,
    val autoPlay: Boolean,
    val startPositionMs: Long = 0L
)

@Serializable
internal data class PlaybackQueueRefreshRequest(
    val queue: List<Track>,
    val index: Int
)

internal fun PlaybackLoadRequest.toCommandExtras(): Bundle = Bundle().apply {
    putString(EXTRA_REQUEST_PAYLOAD, playbackLoadJson.encodeToString(this@toCommandExtras))
}

internal fun Bundle.toPlaybackLoadRequestOrNull(): PlaybackLoadRequest? =
    getString(EXTRA_REQUEST_PAYLOAD)
        ?.let { payload ->
            runCatching { playbackLoadJson.decodeFromString<PlaybackLoadRequest>(payload) }.getOrNull()
        }

internal fun PlaybackQueueRefreshRequest.toCommandExtras(): Bundle = Bundle().apply {
    putString(EXTRA_REQUEST_PAYLOAD, playbackLoadJson.encodeToString(this@toCommandExtras))
}

internal fun Bundle.toPlaybackQueueRefreshRequestOrNull(): PlaybackQueueRefreshRequest? =
    getString(EXTRA_REQUEST_PAYLOAD)
        ?.let { payload ->
            runCatching { playbackLoadJson.decodeFromString<PlaybackQueueRefreshRequest>(payload) }.getOrNull()
        }
