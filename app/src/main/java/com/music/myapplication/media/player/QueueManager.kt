package com.music.myapplication.media.player

import com.music.myapplication.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor() {

    private val _queue = mutableListOf<Track>()
    val queue: List<Track> get() = _queue.toList()
    var currentIndex: Int = -1
        private set

    val currentTrack: Track? get() = _queue.getOrNull(currentIndex)

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        _queue.clear()
        _queue.addAll(tracks)
        currentIndex = if (_queue.isEmpty()) {
            -1
        } else {
            startIndex.coerceIn(0, _queue.lastIndex)
        }
    }

    fun hasNext(): Boolean = currentIndex < _queue.lastIndex

    fun hasPrevious(): Boolean = currentIndex > 0

    fun moveToNext(): Track? {
        if (!hasNext()) return null
        currentIndex++
        return currentTrack
    }

    fun moveToPrevious(): Track? {
        if (!hasPrevious()) return null
        currentIndex--
        return currentTrack
    }

    fun moveToIndex(index: Int): Track? {
        if (index !in _queue.indices) return null
        currentIndex = index
        return currentTrack
    }

    fun addToQueue(track: Track) {
        _queue.add(track)
    }

    fun removeFromQueue(index: Int) {
        if (index !in _queue.indices) return
        _queue.removeAt(index)
        currentIndex = when {
            _queue.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(_queue.lastIndex)
            else -> currentIndex
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from !in _queue.indices || to !in _queue.indices || from == to) return
        val item = _queue.removeAt(from)
        _queue.add(to, item)
        currentIndex = when {
            currentIndex == from -> to
            from < to && currentIndex in (from + 1)..to -> currentIndex - 1
            from > to && currentIndex in to until from -> currentIndex + 1
            else -> currentIndex
        }
    }

    fun clear() {
        _queue.clear()
        currentIndex = -1
    }

    val isEmpty: Boolean get() = _queue.isEmpty()
    val size: Int get() = _queue.size
}
