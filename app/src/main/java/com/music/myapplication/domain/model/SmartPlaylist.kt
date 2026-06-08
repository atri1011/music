package com.music.myapplication.domain.model

data class SmartPlaylist(
    val rule: SmartPlaylistRule,
    val trackCount: Int,
    val previewTracks: List<Track> = emptyList()
)

enum class SmartPlaylistRule(
    val id: String,
    val title: String,
    val description: String
) {
    FREQUENT_UNFAVORITED(
        id = "frequent_unfavorited",
        title = "高频未收藏",
        description = "播放次数 >= 50 且还没收藏"
    ),
    RECENT_WEEK_FAVORITES(
        id = "recent_week_favorites",
        title = "本周新增收藏",
        description = "最近 7 天收藏的歌曲"
    ),
    RECENT_WEEK_REPLAY(
        id = "recent_week_replay",
        title = "本周循环",
        description = "最近 7 天反复播放的歌曲"
    ),
    RECENTLY_PLAYED(
        id = "recently_played",
        title = "刚刚听过",
        description = "最近播放记录自动整理"
    );

    companion object {
        fun fromId(id: String): SmartPlaylistRule? = entries.firstOrNull { it.id == id }
    }
}
