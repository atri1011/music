package com.music.myapplication.media.player

import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.PlaybackMode
import com.music.myapplication.domain.model.Track
import com.music.myapplication.media.state.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackModeManager @Inject constructor(
    private val queueManager: QueueManager,
    private val stateStore: PlaybackStateStore,
    private val preferences: PlayerPreferences
) {

    private var mode: PlaybackMode = PlaybackMode.SEQUENTIAL
    private var shuffleSession: ShuffleSession? = null

    fun setMode(newMode: PlaybackMode) {
        mode = newMode
        stateStore.updatePlaybackMode(mode)
        shuffleSession = if (mode == PlaybackMode.SHUFFLE) {
            buildShuffleSession(
                queueKeys = currentQueueKeys(),
                currentIndex = queueManager.currentIndex
            )
        } else {
            null
        }
    }

    fun toggleMode() {
        setMode(mode.next())
    }

    fun getNextTrack(): Track? = when (mode) {
        PlaybackMode.SEQUENTIAL -> queueManager.moveToNext()
        PlaybackMode.REPEAT_ONE -> queueManager.currentTrack
        PlaybackMode.SHUFFLE -> moveToNextShuffleTrack()
    }

    fun getPreviousTrack(): Track? = when (mode) {
        PlaybackMode.SEQUENTIAL -> queueManager.moveToPrevious()
        PlaybackMode.REPEAT_ONE -> queueManager.currentTrack
        PlaybackMode.SHUFFLE -> moveToPreviousShuffleTrack()
    }

    fun currentMode(): PlaybackMode = mode

    internal fun peekNextQueueIndexForGapless(): Int? {
        if (mode != PlaybackMode.SHUFFLE) return null
        val session = ensureShuffleSession() ?: return null
        session.pendingNextIndex?.let { return it }

        val (updatedSession, nextIndex) = prepareNextShuffleIndex(session) ?: return null
        shuffleSession = updatedSession.copy(pendingNextIndex = nextIndex)
        return nextIndex
    }

    internal fun commitAutoTransitionToQueueIndex(queueIndex: Int) {
        if (mode != PlaybackMode.SHUFFLE) return
        commitShuffleIndex(queueIndex)
    }

    private fun moveToNextShuffleTrack(): Track? {
        val session = ensureShuffleSession() ?: return null
        val nextIndex = session.pendingNextIndex ?: prepareNextShuffleIndex(session)?.let { (updatedSession, preparedIndex) ->
            shuffleSession = updatedSession
            preparedIndex
        } ?: return null
        commitShuffleIndex(nextIndex)
        return queueManager.moveToIndex(nextIndex)
    }

    private fun moveToPreviousShuffleTrack(): Track? {
        val session = ensureShuffleSession() ?: return queueManager.currentTrack
        if (session.currentPosition <= 0) return queueManager.currentTrack
        val previousPosition = session.currentPosition - 1
        val previousIndex = session.order[previousPosition]
        shuffleSession = session.copy(
            currentPosition = previousPosition,
            pendingNextIndex = null
        )
        return queueManager.moveToIndex(previousIndex)
    }

    private fun commitShuffleIndex(queueIndex: Int) {
        val session = ensureShuffleSession() ?: return
        val nextPosition = session.order.indexOf(queueIndex)
        shuffleSession = if (nextPosition >= 0) {
            session.copy(
                currentPosition = nextPosition,
                pendingNextIndex = null
            )
        } else {
            buildShuffleSession(
                queueKeys = currentQueueKeys(),
                currentIndex = queueIndex
            )
        }
    }

    private fun prepareNextShuffleIndex(session: ShuffleSession): Pair<ShuffleSession, Int>? {
        val nextPosition = session.currentPosition + 1
        if (nextPosition < session.order.size) {
            return session to session.order[nextPosition]
        }
        if (session.order.size == 1) {
            return session to session.order.first()
        }

        val rebuiltSession = buildShuffleSession(
            queueKeys = currentQueueKeys(),
            currentIndex = queueManager.currentIndex
        ) ?: return null
        val nextIndex = rebuiltSession.order.getOrNull(1) ?: rebuiltSession.order.firstOrNull() ?: return null
        return rebuiltSession to nextIndex
    }

    private fun ensureShuffleSession(): ShuffleSession? {
        val currentIndex = queueManager.currentIndex
        val queueKeys = currentQueueKeys()
        val existing = shuffleSession
        val recoveredSession = when {
            mode != PlaybackMode.SHUFFLE -> null
            queueKeys.isEmpty() || currentIndex !in queueKeys.indices -> null
            existing == null -> buildShuffleSession(queueKeys, currentIndex)
            existing.queueKeys != queueKeys -> buildShuffleSession(queueKeys, currentIndex)
            existing.order.size != queueKeys.size -> buildShuffleSession(queueKeys, currentIndex)
            existing.order.any { it !in queueKeys.indices } -> buildShuffleSession(queueKeys, currentIndex)
            else -> {
                val recoveredPosition = existing.order.indexOf(currentIndex)
                if (recoveredPosition < 0) {
                    buildShuffleSession(queueKeys, currentIndex)
                } else {
                    existing.copy(
                        currentPosition = recoveredPosition,
                        pendingNextIndex = existing.pendingNextIndex?.takeIf {
                            recoveredPosition == existing.currentPosition && it in queueKeys.indices
                        }
                    )
                }
            }
        }
        shuffleSession = recoveredSession
        return recoveredSession
    }

    private fun buildShuffleSession(queueKeys: List<String>, currentIndex: Int): ShuffleSession? {
        if (queueKeys.isEmpty() || currentIndex !in queueKeys.indices) return null
        val remainingOrder = queueKeys.indices
            .filter { it != currentIndex }
            .shuffled()
        return ShuffleSession(
            queueKeys = queueKeys,
            order = listOf(currentIndex) + remainingOrder,
            currentPosition = 0,
            pendingNextIndex = null
        )
    }

    private fun currentQueueKeys(): List<String> = queueManager.queue.mapIndexed { index, track ->
        "$index:${track.platform.id}:${track.id}"
    }

    private data class ShuffleSession(
        val queueKeys: List<String>,
        val order: List<Int>,
        val currentPosition: Int,
        val pendingNextIndex: Int?
    )
}
