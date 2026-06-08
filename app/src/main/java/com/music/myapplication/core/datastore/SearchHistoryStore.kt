package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryStore by preferencesDataStore("search_history")

@Serializable
data class SearchHistoryResultEntry(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val platformId: String,
    val type: String,
    val trackCount: Int = 0
)

@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val HISTORY = stringPreferencesKey("history")
        val RECENT_RESULTS = stringPreferencesKey("recent_results")
    }

    val history: Flow<List<String>> = context.searchHistoryStore.data
        .map { prefs ->
            prefs[Keys.HISTORY]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
        }
        .catch { emit(emptyList()) }

    val recentResults: Flow<List<SearchHistoryResultEntry>> = context.searchHistoryStore.data
        .map { prefs ->
            prefs[Keys.RECENT_RESULTS]?.let {
                runCatching { json.decodeFromString<List<SearchHistoryResultEntry>>(it) }
                    .getOrDefault(emptyList())
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

    suspend fun recordResult(entry: SearchHistoryResultEntry) {
        if (entry.id.isBlank() || entry.title.isBlank()) return
        context.searchHistoryStore.edit { prefs ->
            val current = prefs[Keys.RECENT_RESULTS]?.let {
                runCatching { json.decodeFromString<List<SearchHistoryResultEntry>>(it) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val entryKey = entry.uniqueKey()
            val updated = listOf(entry) + current.filter { it.uniqueKey() != entryKey }
            prefs[Keys.RECENT_RESULTS] = json.encodeToString(updated.take(MAX_RECENT_RESULTS))
        }
    }

    suspend fun clear() {
        context.searchHistoryStore.edit {
            it.remove(Keys.HISTORY)
            it.remove(Keys.RECENT_RESULTS)
        }
    }

    private fun SearchHistoryResultEntry.uniqueKey(): String = "$type:$platformId:$id"

    private companion object {
        const val MAX_HISTORY = 20
        const val MAX_RECENT_RESULTS = 12
    }
}
