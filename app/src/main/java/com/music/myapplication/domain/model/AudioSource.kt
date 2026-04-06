package com.music.myapplication.domain.model

enum class AudioSource(val id: String, val displayName: String) {
    TUNEHUB("tunehub", "TuneHub"),
    LX_CUSTOM("lx_custom", "LX Music 自定义源"),
    METING_BAKA("meting_baka", "Meting (baka.plus)"),
    JKAPI("jkapi", "JKAPI (无铭API)"),
    NETEASE_CLOUD_API_ENHANCED("netease_cloud_api_enhanced", "网易云增强版 API");

    val supportedPlatforms: List<Platform>
        get() = when (this) {
            TUNEHUB -> listOf(Platform.NETEASE, Platform.QQ, Platform.KUWO)
            LX_CUSTOM -> listOf(Platform.NETEASE, Platform.QQ, Platform.KUWO)
            METING_BAKA -> listOf(Platform.NETEASE, Platform.QQ)
            JKAPI -> listOf(Platform.NETEASE, Platform.QQ)
            NETEASE_CLOUD_API_ENHANCED -> listOf(Platform.NETEASE)
        }

    fun supports(platform: Platform): Boolean = supportedPlatforms.contains(platform)

    companion object {
        fun fromId(id: String): AudioSource =
            entries.firstOrNull { it.id == id } ?: TUNEHUB
    }
}
