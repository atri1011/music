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
    private val jkApiResolver: JkApiPlayableResolver
) {
    suspend fun resolve(track: Track, quality: String): Result<String> {
        val source = preferences.audioSource.first()
        return when (source) {
            AudioSource.TUNEHUB -> tuneHubResolver.resolve(track, quality)
            AudioSource.JKAPI -> {
                if (track.platform == Platform.KUWO) {
                    return tuneHubResolver.resolve(track, quality)
                }
                when (val jkResult = jkApiResolver.resolve(track)) {
                    is Result.Success -> jkResult
                    is Result.Error -> tuneHubResolver.resolve(track, quality)
                    Result.Loading -> tuneHubResolver.resolve(track, quality)
                }
            }
        }
    }
}
