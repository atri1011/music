package com.music.myapplication.domain.model

enum class AudioSource(val id: String, val displayName: String) {
    TUNEHUB("tunehub", "TuneHub"),
    JKAPI("jkapi", "JKAPI (无铭API)");

    companion object {
        fun fromId(id: String): AudioSource =
            entries.firstOrNull { it.id == id } ?: TUNEHUB
    }
}
