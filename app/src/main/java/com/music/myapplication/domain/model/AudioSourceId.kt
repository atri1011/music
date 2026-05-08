package com.music.myapplication.domain.model

@JvmInline
value class AudioSourceId(val value: String) {
    companion object {
        val TUNEHUB = AudioSourceId("tunehub")
        val LX_CUSTOM = AudioSourceId("lx_custom")
    }
}
