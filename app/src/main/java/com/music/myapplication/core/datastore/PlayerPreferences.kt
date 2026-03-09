package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

enum class DarkModeOption { FOLLOW_SYSTEM, DARK, LIGHT }

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
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
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

    val darkMode: Flow<DarkModeOption> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE]?.let { runCatching { DarkModeOption.valueOf(it) }.getOrNull() }
            ?: DarkModeOption.FOLLOW_SYSTEM
    }

    val autoPlay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_PLAY] ?: true
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: false
    }

    val cacheLimitMb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHE_LIMIT_MB] ?: 500
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

    suspend fun setDarkMode(option: DarkModeOption) {
        context.dataStore.edit { it[Keys.DARK_MODE] = option.name }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY] = enabled }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { it[Keys.CACHE_LIMIT_MB] = limitMb }
    }
}
