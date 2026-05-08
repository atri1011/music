package com.music.myapplication.domain.model

@Deprecated("Use AudioSourceDescriptor instead", ReplaceWith("AudioSourceDescriptor"))
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

    fun toDescriptor(): AudioSourceDescriptor = when (this) {
        TUNEHUB -> AudioSourceDescriptor.Native.TuneHub()
        LX_CUSTOM -> AudioSourceDescriptor.Native.LxCustom()
        METING_BAKA -> AudioSourceDescriptor.Recipe(
            recipe = PlayableUrlRecipe(
                id = id,
                displayName = displayName,
                supportedPlatforms = listOf("NETEASE", "QQ"),
                platformVars = mapOf(
                    "NETEASE" to mapOf("server" to "netease"),
                    "QQ" to mapOf("server" to "tencent")
                ),
                qualityMap = mapOf(
                    "128k" to "128",
                    "320k" to "320",
                    "flac" to "380"
                ),
                request = RecipeRequest(
                    method = "HEAD",
                    url = "https://api.baka.plus/meting/?server={{platformVars.server}}&type=url&id={{trackId}}&br={{qualityMapped}}",
                    followRedirects = false
                ),
                extract = RecipeExtract(kind = "redirect_location")
            )
        )
        JKAPI -> AudioSourceDescriptor.Recipe(
            recipe = PlayableUrlRecipe(
                id = id,
                displayName = displayName,
                supportedPlatforms = listOf("NETEASE", "QQ"),
                auth = listOf(
                    RecipeAuthField(
                        key = "apiKey",
                        label = "API 密钥",
                        type = "password"
                    )
                ),
                platformVars = mapOf(
                    "NETEASE" to mapOf("platCode" to "wy"),
                    "QQ" to mapOf("platCode" to "qq")
                ),
                qualityMap = mapOf(
                    "128k" to "128",
                    "320k" to "320",
                    "flac" to "flac"
                ),
                request = RecipeRequest(
                    url = "https://jkapi.com/api/music?plat={{platformVars.platCode}}&apiKey={{auth.apiKey}}&name={{encodeURIComponent(title)}} {{encodeURIComponent(artist)}}"
                ),
                extract = RecipeExtract(
                    kind = "json_path",
                    successCondition = RecipeSuccessCondition(
                        path = "code",
                        equals = kotlinx.serialization.json.JsonPrimitive(1)
                    ),
                    playableUrlPath = "musicUrl",
                    errorMessagePath = "msg"
                ),
                validate = RecipeValidate(
                    titleSimilarity = RecipeTitleSimilarity(
                        responsePath = "name",
                        trackField = "title",
                        min = 0.4f
                    )
                )
            )
        )
        NETEASE_CLOUD_API_ENHANCED -> AudioSourceDescriptor.Recipe(
            recipe = PlayableUrlRecipe(
                id = id,
                displayName = displayName,
                supportedPlatforms = listOf("NETEASE"),
                auth = listOf(
                    RecipeAuthField(
                        key = "baseUrl",
                        label = "接口地址",
                        type = "text"
                    )
                ),
                platformVars = mapOf("NETEASE" to emptyMap()),
                qualityMap = mapOf(
                    "128k" to "standard",
                    "320k" to "exhigh",
                    "flac" to "lossless"
                ),
                request = RecipeRequest(
                    url = "{{auth.baseUrl}}song/url/v1?id={{trackId}}&level={{qualityMapped}}"
                ),
                extract = RecipeExtract(
                    kind = "json_path",
                    successCondition = RecipeSuccessCondition(
                        path = "code",
                        equals = kotlinx.serialization.json.JsonPrimitive(200)
                    ),
                    playableUrlPath = "data.0.url"
                )
            )
        )
    }

    companion object {
        fun fromId(id: String): AudioSource =
            entries.firstOrNull { it.id == id } ?: TUNEHUB
    }
}
