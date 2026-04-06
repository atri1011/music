package com.music.myapplication.data.repository.lx

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.Serializable

@Serializable
data class LxScriptCatalog(
    val scripts: List<LxCustomScript> = emptyList(),
    val activeScriptId: String? = null
) {
    val activeScript: LxCustomScript?
        get() = scripts.firstOrNull { it.id == activeScriptId }

    val activeValidatedScript: LxCustomScript?
        get() = activeScript?.takeIf { it.isValidationPassed }
}

@Serializable
data class LxCustomScript(
    val id: String,
    val rawScript: String,
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val declaredSources: List<String> = emptyList(),
    val lastValidatedAt: Long? = null,
    val lastValidationError: String? = null,
    val updatedAt: Long = 0L,
    val updateAlertLog: String? = null,
    val updateAlertUrl: String? = null
) {
    val isValidationPassed: Boolean
        get() = lastValidatedAt != null && lastValidationError.isNullOrBlank()

    val displayTitle: String
        get() = name.ifBlank { "未命名脚本" }
}

data class LxScriptCatalogSummary(
    val scriptCount: Int = 0,
    val validScriptCount: Int = 0,
    val activeScriptId: String? = null,
    val activeScriptName: String = "",
    val activeScriptVersion: String = "",
    val activeScriptSources: List<String> = emptyList(),
    val activeScriptValidationError: String? = null
) {
    val hasActiveValidatedScript: Boolean
        get() = activeScriptId != null && activeScriptValidationError.isNullOrBlank()
}

data class LxScriptValidationResult(
    val metadata: LxScriptMetadata,
    val declaredSources: List<LxDeclaredSource>,
    val updateAlert: LxUpdateAlertInfo?,
    val validationError: String? = null
) {
    val isSuccess: Boolean
        get() = validationError.isNullOrBlank()
}

data class LxImportedScriptContent(
    val rawScript: String,
    val sourceLabel: String
)

@Serializable
data class LxScriptMetadata(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val rawScript: String = ""
)

@Serializable
data class LxDeclaredSource(
    val id: String,
    val type: String,
    val actions: List<String>,
    val qualities: List<String>
)

@Serializable
data class LxUpdateAlertInfo(
    val scriptId: String,
    val scriptName: String,
    val scriptVersion: String,
    val log: String,
    val updateUrl: String?
) {
    val dedupeKey: String
        get() = listOf(scriptId, scriptVersion.ifBlank { "unknown" }, log).joinToString("#")
}

@Serializable
data class LxMusicRequestPayload(
    val type: String,
    val musicInfo: LxMusicInfo
)

@Serializable
data class LxMusicInfo(
    val id: String,
    val songmid: String,
    val mid: String,
    val name: String,
    val title: String,
    val artist: String,
    val singer: String,
    val album: String,
    val albumName: String,
    val albumId: String,
    val pic: String,
    val picUrl: String,
    val interval: Int,
    val duration: Int,
    val source: String
)

enum class LxKnownSource(
    val id: String,
    val displayName: String,
    val mappedPlatform: Platform?
) {
    NETEASE("wy", "网易云", Platform.NETEASE),
    QQ("tx", "QQ 音乐", Platform.QQ),
    KUWO("kw", "酷我", Platform.KUWO),
    KUGOU("kg", "酷狗", null),
    MIGU("mg", "咪咕", null),
    LOCAL("local", "本地", Platform.LOCAL);

    companion object {
        fun fromId(id: String): LxKnownSource? = entries.firstOrNull { it.id == id }
    }
}

internal fun LxScriptCatalog.toSummary(): LxScriptCatalogSummary {
    val active = activeScript
    return LxScriptCatalogSummary(
        scriptCount = scripts.size,
        validScriptCount = scripts.count { it.isValidationPassed },
        activeScriptId = active?.id,
        activeScriptName = active?.displayTitle.orEmpty(),
        activeScriptVersion = active?.version.orEmpty(),
        activeScriptSources = active?.declaredSources.orEmpty(),
        activeScriptValidationError = active?.lastValidationError
    )
}

internal fun List<String>.toLxSourceDisplayText(): String {
    if (isEmpty()) return "未声明支持来源"
    return joinToString("、") { sourceId ->
        LxKnownSource.fromId(sourceId)?.displayName ?: sourceId
    }
}

internal fun Track.toLxMusicInfo(
    quality: String,
    sourceId: String,
    qqSongMidCandidate: String?
): LxMusicInfo {
    val qqMid = qqSongMidCandidate
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: id
    val durationSeconds = (durationMs / 1_000L).coerceAtLeast(0L).toInt()
    return LxMusicInfo(
        id = id,
        songmid = qqMid,
        mid = qqMid,
        name = title,
        title = title,
        artist = artist,
        singer = artist,
        album = album,
        albumName = album,
        albumId = albumId,
        pic = coverUrl,
        picUrl = coverUrl,
        interval = durationSeconds,
        duration = durationSeconds,
        source = sourceId
    )
}
