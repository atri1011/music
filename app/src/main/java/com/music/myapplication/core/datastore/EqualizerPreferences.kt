package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.equalizerDataStore by preferencesDataStore("equalizer_preferences")

@Singleton
class EqualizerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val ENABLED = booleanPreferencesKey("equalizer_enabled")
        val PRESET_INDEX = intPreferencesKey("equalizer_preset_index")
        val CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
    }

    val enabled: Flow<Boolean> = context.equalizerDataStore.data.map { prefs ->
        prefs[Keys.ENABLED] ?: false
    }

    val presetIndex: Flow<Int> = context.equalizerDataStore.data.map { prefs ->
        prefs[Keys.PRESET_INDEX] ?: 0
    }

    val customBandLevels: Flow<Map<Int, Int>> = context.equalizerDataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_BANDS]
            ?.takeIf { it.isNotBlank() }
            ?.let { payload ->
                runCatching {
                    json.decodeFromString<Map<String, Int>>(payload)
                        .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
                        .toMap()
                }.getOrDefault(emptyMap())
            }
            ?: emptyMap()
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.equalizerDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setPresetIndex(index: Int) {
        context.equalizerDataStore.edit { it[Keys.PRESET_INDEX] = index }
    }

    suspend fun setCustomBandLevels(levels: Map<Int, Int>) {
        context.equalizerDataStore.edit { prefs ->
            if (levels.isEmpty()) {
                prefs[Keys.CUSTOM_BANDS] = ""
            } else {
                val serializable = levels.toSortedMap().mapKeys { (k, _) -> k.toString() }
                prefs[Keys.CUSTOM_BANDS] = json.encodeToString(
                    kotlinx.serialization.serializer<Map<String, Int>>(),
                    serializable
                )
            }
        }
    }
}
