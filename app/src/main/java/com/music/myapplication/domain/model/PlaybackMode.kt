package com.music.myapplication.domain.model

enum class PlaybackMode {
    SEQUENTIAL,
    SHUFFLE,
    REPEAT_ONE;

    fun next(): PlaybackMode = when (this) {
        SEQUENTIAL -> SHUFFLE
        SHUFFLE -> REPEAT_ONE
        REPEAT_ONE -> SEQUENTIAL
    }
}
