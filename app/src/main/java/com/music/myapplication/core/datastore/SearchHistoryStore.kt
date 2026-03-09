package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryStore by preferencesDataStore("search_history")

@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val HISTORY = stringPreferencesKey("history")
    }

    val history: Flow<List<String>> = context.searchHistoryStore.data
        .map { prefs ->
            prefs[Keys.HISTORY]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
        }
        .catch { emit(emptyList()) }

    suspend fun record(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        context.searchHistoryStore.edit { prefs ->
            val current = prefs[Keys.HISTORY]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = listOf(trimmed) + current.filter { it != trimmed }
            prefs[Keys.HISTORY] = json.encodeToString(updated.take(MAX_HISTORY))
        }
    }

    suspend fun remove(keyword: String) {
        context.searchHistoryStore.edit { prefs ->
            val current = prefs[Keys.HISTORY]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[Keys.HISTORY] = json.encodeToString(current.filter { it != keyword })
        }
    }

    suspend fun clear() {
        context.searchHistoryStore.edit { it.remove(Keys.HISTORY) }
    }

    private companion object {
        const val MAX_HISTORY = 20
    }
}
