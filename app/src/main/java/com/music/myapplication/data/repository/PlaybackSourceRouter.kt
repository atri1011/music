package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.data.repository.recipe.RecipePlayableResolver
import com.music.myapplication.data.repository.recipe.RecipeRegistry
import com.music.myapplication.domain.model.AudioSourceDescriptor
import com.music.myapplication.domain.model.AudioSourceId
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
    private val recipeRegistry: RecipeRegistry,
    private val recipeResolver: RecipePlayableResolver
) {
    suspend fun currentRequestedSource(): AudioSourceDescriptor {
        recipeRegistry.initialize()
        val sourceId = preferences.audioSourceId.first()
        return recipeRegistry.find(AudioSourceId(sourceId))
            ?: AudioSourceDescriptor.fromId(sourceId)
    }

    suspend fun resolve(track: Track, quality: String): Result<PlaybackSourceResolution> {
        val requestedSource = currentRequestedSource()
        return resolve(track, quality, requestedSource)
    }

    suspend fun resolve(
        track: Track,
        quality: String,
        requestedSource: AudioSourceDescriptor
    ): Result<PlaybackSourceResolution> {
        return when (requestedSource) {
            is AudioSourceDescriptor.Native -> when (requestedSource.kind) {
                AudioSourceDescriptor.NativeKind.TUNEHUB -> resolveDirectly(requestedSource) {
                    tuneHubResolver.resolve(track, quality)
                }
                AudioSourceDescriptor.NativeKind.LX_CUSTOM -> resolveWithTuneHubFallback(
                    track = track,
                    quality = quality,
                    requestedSource = requestedSource,
                    resolveRequested = { lxCustomSourcePlayableResolver.resolve(track, quality) }
                )
            }
            is AudioSourceDescriptor.Recipe -> resolveWithTuneHubFallback(
                track = track,
                quality = quality,
                requestedSource = requestedSource,
                resolveRequested = {
                    if (!requestedSource.supports(track.platform)) {
                        Result.Error(
                            com.music.myapplication.core.common.AppError.Api(
                                message = "${requestedSource.displayName} 不支持${track.platform.displayName}"
                            )
                        )
                    } else {
                        recipeResolver.resolve(track, quality, requestedSource.recipe)
                    }
                }
            )
        }
    }

    private suspend fun resolveDirectly(
        requestedSource: AudioSourceDescriptor,
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
        requestedSource: AudioSourceDescriptor,
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
        requestedSource: AudioSourceDescriptor,
        fallbackReason: String
    ): Result<PlaybackSourceResolution> {
        val tuneHub = AudioSourceDescriptor.Native.TuneHub()
        return when (val tuneHubResult = tuneHubResolver.resolve(track, quality)) {
            is Result.Success -> Result.Success(
                PlaybackSourceResolution(
                    playableUrl = tuneHubResult.data,
                    requestedSource = requestedSource,
                    resolvedSource = tuneHub,
                    didFallback = true,
                    fallbackReason = fallbackReason
                )
            )
            is Result.Error -> Result.Error(tuneHubResult.error)
            Result.Loading -> Result.Loading
        }
    }

    private fun buildUnsupportedFallbackReason(
        source: AudioSourceDescriptor,
        platform: Platform
    ): String {
        return "${source.displayName} 不支持${platform.displayName}，已自动切到 TuneHub"
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
