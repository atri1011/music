package com.music.myapplication.media.service

import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track

internal data class GaplessPlaybackWindow(
    val current: GaplessQueueTrack,
    val next: GaplessQueueTrack?
)

internal data class GaplessQueueTrack(
    val queueIndex: Int,
    val track: Track
)

private const val PLAYBACK_QUEUE_MEDIA_ID_PREFIX = "__playback_queue__"

internal fun buildGaplessPlaybackWindow(
    queue: List<Track>,
    currentIndex: Int,
    autoPlay: Boolean,
    playbackMode: PlaybackMode,
    crossfadeEnabled: Boolean
): GaplessPlaybackWindow? {
    val currentTrack = queue.getOrNull(currentIndex) ?: return null
    val current = GaplessQueueTrack(queueIndex = currentIndex, track = currentTrack)
    val next = when {
        !shouldPreloadGaplessTrack(autoPlay, playbackMode, crossfadeEnabled) -> null
        playbackMode == PlaybackMode.SEQUENTIAL -> {
            queue.getOrNull(currentIndex + 1)?.let { track ->
                GaplessQueueTrack(queueIndex = currentIndex + 1, track = track)
            }
        }
        playbackMode == PlaybackMode.REPEAT_ONE -> current
        else -> null
    }
    return GaplessPlaybackWindow(current = current, next = next)
}

internal fun buildPlaybackQueueMediaId(track: Track, queueIndex: Int): String =
    "$PLAYBACK_QUEUE_MEDIA_ID_PREFIX:$queueIndex:${track.platform.id}:${track.id}"

internal fun playbackQueueIndexFromMediaId(mediaId: String?): Int? {
    val prefix = "$PLAYBACK_QUEUE_MEDIA_ID_PREFIX:"
    if (mediaId.isNullOrBlank() || !mediaId.startsWith(prefix)) return null
    return mediaId
        .removePrefix(prefix)
        .substringBefore(':')
        .toIntOrNull()
        ?.takeIf { it >= 0 }
}

private fun shouldPreloadGaplessTrack(
    autoPlay: Boolean,
    playbackMode: PlaybackMode,
    crossfadeEnabled: Boolean
): Boolean = autoPlay &&
    !crossfadeEnabled &&
    when (playbackMode) {
        PlaybackMode.SEQUENTIAL,
        PlaybackMode.REPEAT_ONE -> true
        PlaybackMode.SHUFFLE -> false
    }
