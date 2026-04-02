package com.music.myapplication.media.state

import com.music.myapplication.core.datastore.PlaybackSnapshot
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.player.QueueManager

internal data class PlaybackRestorePlan(
    val track: Track,
    val queue: List<Track>,
    val index: Int,
    val positionMs: Long
)

internal fun buildPlaybackRestorePlan(snapshot: PlaybackSnapshot?): PlaybackRestorePlan? =
    snapshot?.let {
        buildPlaybackRestorePlan(
            currentTrack = it.currentTrack,
            queue = it.queue,
            currentIndex = it.currentIndex,
            positionMs = it.positionMs
        )
    }

internal fun buildPlaybackRestorePlan(
    currentTrack: Track?,
    queue: List<Track>,
    currentIndex: Int,
    positionMs: Long
): PlaybackRestorePlan? {
    val normalizedQueue = queue.ifEmpty { listOfNotNull(currentTrack) }
    val restoredTrack = currentTrack
        ?: normalizedQueue.getOrNull(currentIndex)
        ?: normalizedQueue.firstOrNull()
        ?: return null
    val restoredIndex = when {
        currentIndex in normalizedQueue.indices -> currentIndex
        else -> normalizedQueue.indexOfFirst { it.restoreKey() == restoredTrack.restoreKey() }
            .takeIf { it >= 0 } ?: 0
    }
    return PlaybackRestorePlan(
        track = restoredTrack,
        queue = normalizedQueue,
        index = restoredIndex,
        positionMs = positionMs.coerceAtLeast(0L)
    )
}

internal fun applyPlaybackRestorePlan(
    plan: PlaybackRestorePlan,
    queueManager: QueueManager,
    stateStore: PlaybackStateStore
) {
    queueManager.setQueue(plan.queue, plan.index)
    if (queueManager.currentIndex >= 0) {
        queueManager.updateTrack(queueManager.currentIndex, plan.track)
    }
    stateStore.updateTrack(plan.track)
    stateStore.updateQueue(queueManager.queue, queueManager.currentIndex)
    stateStore.updatePosition(plan.positionMs)
    stateStore.updateDuration(plan.track.durationMs.coerceAtLeast(0L))
    stateStore.updatePlaying(false)
}

private fun Track.restoreKey(): String = "${platform.id}:$id"
