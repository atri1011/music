package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.homeContentCacheDataStore by preferencesDataStore("home_content_cache")

@Singleton
class HomeContentCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {

    suspend fun getCachedToplists(platform: Platform): List<ToplistInfo>? =
        getDailyValue(
            dateKey = stringPreferencesKey("toplists_${platform.id}_date"),
            dataKey = stringPreferencesKey("toplists_${platform.id}_data")
        )

    suspend fun cacheToplists(platform: Platform, toplists: List<ToplistInfo>) {
        if (toplists.isEmpty()) return
        putDailyValue(
            dateKey = stringPreferencesKey("toplists_${platform.id}_date"),
            dataKey = stringPreferencesKey("toplists_${platform.id}_data"),
            value = toplists
        )
    }

    suspend fun getCachedToplistDetail(platform: Platform, toplistId: String): List<Track>? {
        val safeId = toplistId.asPreferenceKeyPart()
        return getDailyValue(
            dateKey = stringPreferencesKey("toplist_detail_${platform.id}_${safeId}_date"),
            dataKey = stringPreferencesKey("toplist_detail_${platform.id}_${safeId}_data")
        )
    }

    suspend fun cacheToplistDetail(platform: Platform, toplistId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val safeId = toplistId.asPreferenceKeyPart()
        putDailyValue(
            dateKey = stringPreferencesKey("toplist_detail_${platform.id}_${safeId}_date"),
            dataKey = stringPreferencesKey("toplist_detail_${platform.id}_${safeId}_data"),
            value = tracks
        )
    }

    suspend fun getCachedRecommendedPlaylists(): List<ToplistInfo>? =
        getDailyValue(
            dateKey = stringPreferencesKey("recommended_playlists_date"),
            dataKey = stringPreferencesKey("recommended_playlists_data")
        )

    suspend fun cacheRecommendedPlaylists(playlists: List<ToplistInfo>) {
        if (playlists.isEmpty()) return
        putDailyValue(
            dateKey = stringPreferencesKey("recommended_playlists_date"),
            dataKey = stringPreferencesKey("recommended_playlists_data"),
            value = playlists
        )
    }

    private suspend inline fun <reified T> getDailyValue(
        dateKey: Preferences.Key<String>,
        dataKey: Preferences.Key<String>
    ): T? {
        val preferences = readPreferences()
        if (preferences[dateKey] != currentDayKey()) return null
        val payload = preferences[dataKey].orEmpty()
        if (payload.isBlank()) return null
        return runCatching { json.decodeFromString<T>(payload) }.getOrNull()
    }

    private suspend inline fun <reified T> putDailyValue(
        dateKey: Preferences.Key<String>,
        dataKey: Preferences.Key<String>,
        value: T
    ) {
        context.homeContentCacheDataStore.edit { preferences ->
            preferences[dateKey] = currentDayKey()
            preferences[dataKey] = json.encodeToString(value)
        }
    }

    private suspend fun readPreferences(): Preferences =
        runCatching { context.homeContentCacheDataStore.data.first() }
            .getOrElse { emptyPreferences() }

    private fun currentDayKey(): String = LocalDate.now().toString()
}

private fun String.asPreferenceKeyPart(): String =
    if (isBlank()) {
        "blank"
    } else {
        buildString(length) {
            for (char in this@asPreferenceKeyPart) {
                append(
                    when {
                        char.isLetterOrDigit() || char == '_' || char == '-' -> char
                        else -> '_'
                    }
                )
            }
        }
    }
