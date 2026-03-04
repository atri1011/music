package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.music.myapplication.domain.model.PlaybackMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("player_preferences")

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        val QUALITY = stringPreferencesKey("quality")
        val PLATFORM = stringPreferencesKey("platform")
        val API_KEY = stringPreferencesKey("api_key")
    }

    @Volatile
    private var apiKeyCache: String = ""

    val playbackMode: Flow<PlaybackMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.PLAYBACK_MODE]?.let { PlaybackMode.valueOf(it) } ?: PlaybackMode.SEQUENTIAL
    }

    val quality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.QUALITY] ?: "128k"
    }

    val platform: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.PLATFORM] ?: "netease"
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.API_KEY] ?: ""
    }

    val currentApiKey: String
        get() = apiKeyCache

    init {
        scope.launch {
            apiKey
                .catch { emit("") }
                .collect { key -> apiKeyCache = key }
        }
    }

    suspend fun setPlaybackMode(mode: PlaybackMode) {
        context.dataStore.edit { it[Keys.PLAYBACK_MODE] = mode.name }
    }

    suspend fun setQuality(quality: String) {
        context.dataStore.edit { it[Keys.QUALITY] = quality }
    }

    suspend fun setPlatform(platform: String) {
        context.dataStore.edit { it[Keys.PLATFORM] = platform }
    }

    suspend fun setApiKey(apiKey: String) {
        val normalized = apiKey
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF\\s]+"), "")
        apiKeyCache = normalized
        context.dataStore.edit { it[Keys.API_KEY] = normalized }
    }
}
