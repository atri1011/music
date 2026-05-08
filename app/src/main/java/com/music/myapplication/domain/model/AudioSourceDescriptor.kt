package com.music.myapplication.domain.model

sealed interface AudioSourceDescriptor {
    val id: AudioSourceId
    val displayName: String
    val supportedPlatforms: List<Platform>

    sealed class Native : AudioSourceDescriptor {
        abstract val kind: NativeKind

        data class TuneHub(
            override val id: AudioSourceId = AudioSourceId.TUNEHUB,
            override val displayName: String = "TuneHub",
            override val supportedPlatforms: List<Platform> = listOf(Platform.NETEASE, Platform.QQ, Platform.KUWO),
            override val kind: NativeKind = NativeKind.TUNEHUB
        ) : Native()

        data class LxCustom(
            override val id: AudioSourceId = AudioSourceId.LX_CUSTOM,
            override val displayName: String = "LX Music 自定义源",
            override val supportedPlatforms: List<Platform> = listOf(Platform.NETEASE, Platform.QQ, Platform.KUWO),
            override val kind: NativeKind = NativeKind.LX_CUSTOM
        ) : Native()
    }

    data class Recipe(
        val recipe: PlayableUrlRecipe,
        override val id: AudioSourceId = AudioSourceId(recipe.id),
        override val displayName: String = recipe.displayName,
        override val supportedPlatforms: List<Platform> = recipe.supportedPlatforms.mapNotNull {
            runCatching { Platform.valueOf(it.uppercase()) }.getOrNull()
        }
    ) : AudioSourceDescriptor

    fun supports(platform: Platform): Boolean = supportedPlatforms.contains(platform)

    enum class NativeKind { TUNEHUB, LX_CUSTOM }

    companion object {
        fun fromId(id: String): AudioSourceDescriptor =
            when (id) {
                Native.TuneHub().id.value -> Native.TuneHub()
                Native.LxCustom().id.value -> Native.LxCustom()
                else -> AudioSource.entries.firstOrNull { it.id == id }?.toDescriptor()
                    ?: Native.TuneHub()
            }
    }
}
