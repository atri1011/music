package com.music.myapplication.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val id: String, val displayName: String) {
    NETEASE("netease", "网易云"),
    QQ("qq", "QQ音乐"),
    KUWO("kuwo", "酷我");

    companion object {
        fun fromId(id: String): Platform =
            entries.firstOrNull { it.id == id } ?: NETEASE
    }
}
