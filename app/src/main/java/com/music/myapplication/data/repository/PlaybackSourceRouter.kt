package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.AudioSource
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSourceRouter @Inject constructor(
    private val preferences: PlayerPreferences,
    private val tuneHubResolver: TuneHubPlayableResolver,
    private val lxCustomSourcePlayableResolver: LxCustomSourcePlayableResolver,
    private val metingPlayableResolver: MetingPlayableResolver,
    private val jkApiResolver: JkApiPlayableResolver,
    private val neteaseCloudApiResolver: NeteaseCloudApiPlayableResolver
) {
    suspend fun resolve(track: Track, quality: String): Result<PlaybackSourceResolution> {
        val requestedSource = preferences.audioSource.first()
        return when (requestedSource) {
            AudioSource.TUNEHUB -> resolveDirectly(requestedSource) {
                tuneHubResolver.resolve(track, quality)
            }
            AudioSource.LX_CUSTOM -> resolveWithTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                resolveRequested = { lxCustomSourcePlayableResolver.resolve(track, quality) }
            )
            AudioSource.METING_BAKA -> resolveWithTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                resolveRequested = { metingPlayableResolver.resolve(track, quality) }
            )
            AudioSource.JKAPI -> resolveWithTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                resolveRequested = { jkApiResolver.resolve(track) }
            )
            AudioSource.NETEASE_CLOUD_API_ENHANCED -> resolveWithTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                resolveRequested = { neteaseCloudApiResolver.resolve(track, quality) }
            )
        }
    }

    private suspend fun resolveDirectly(
        requestedSource: AudioSource,
        resolveRequested: suspend () -> Result<String>
    ): Result<PlaybackSourceResolution> {
        return when (val result = resolveRequested()) {
            is Result.Success -> Result.Success(
                PlaybackSourceResolution(
                    playableUrl = result.data,
                    requestedSource = requestedSource,
                    resolvedSource = requestedSource,
                    didFallback = false
                )
            )
            is Result.Error -> Result.Error(result.error)
            Result.Loading -> Result.Loading
        }
    }

    private suspend fun resolveWithTuneHubFallback(
        track: Track,
        quality: String,
        requestedSource: AudioSource,
        resolveRequested: suspend () -> Result<String>
    ): Result<PlaybackSourceResolution> {
        if (!requestedSource.supports(track.platform)) {
            return resolveTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                fallbackReason = buildUnsupportedFallbackReason(requestedSource, track.platform)
            )
        }

        return when (val requestedResult = resolveRequested()) {
            is Result.Success -> Result.Success(
                PlaybackSourceResolution(
                    playableUrl = requestedResult.data,
                    requestedSource = requestedSource,
                    resolvedSource = requestedSource,
                    didFallback = false
                )
            )
            is Result.Error -> resolveTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                fallbackReason = requestedResult.error.message.toFallbackReason()
            )
            Result.Loading -> resolveTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                fallbackReason = "${requestedSource.displayName} 暂时不可用，已自动切到 TuneHub"
            )
        }
    }

    private suspend fun resolveTuneHubFallback(
        track: Track,
        quality: String,
        requestedSource: AudioSource,
        fallbackReason: String
    ): Result<PlaybackSourceResolution> {
        return when (val tuneHubResult = tuneHubResolver.resolve(track, quality)) {
            is Result.Success -> Result.Success(
                PlaybackSourceResolution(
                    playableUrl = tuneHubResult.data,
                    requestedSource = requestedSource,
                    resolvedSource = AudioSource.TUNEHUB,
                    didFallback = true,
                    fallbackReason = fallbackReason
                )
            )
            is Result.Error -> Result.Error(tuneHubResult.error)
            Result.Loading -> Result.Loading
        }
    }

    private fun buildUnsupportedFallbackReason(source: AudioSource, platform: Platform): String {
        return when (source) {
            AudioSource.LX_CUSTOM -> "${source.displayName} 不支持${platform.displayName}，已自动切到 TuneHub"
            AudioSource.METING_BAKA,
            AudioSource.JKAPI -> "${source.displayName} 不支持${platform.displayName}，已自动切到 TuneHub"
            AudioSource.NETEASE_CLOUD_API_ENHANCED -> {
                "${source.displayName} 仅支持网易云歌曲，已自动切到 TuneHub"
            }
            AudioSource.TUNEHUB -> "${source.displayName} 已继续使用"
        }
    }
}

private fun String.toFallbackReason(): String {
    val trimmed = trim().trimEnd('。', '，', ',', '；', ';')
    return if (trimmed.isBlank()) {
        "当前音源不可用，已自动切到 TuneHub"
    } else {
        "$trimmed，已自动切到 TuneHub"
    }
}
