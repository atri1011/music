package com.music.myapplication.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val id: String, val displayName: String) {
    NETEASE("netease", "网易云"),
    QQ("qq", "QQ音乐"),
    KUWO("kuwo", "酷我"),
    LOCAL("local", "本地");

    companion object {
        fun fromId(id: String): Platform =
            entries.firstOrNull { it.id == id } ?: NETEASE

        /** Online platforms only (excludes LOCAL) */
        val onlinePlatforms: List<Platform>
            get() = listOf(NETEASE, QQ, KUWO)
    }
}
