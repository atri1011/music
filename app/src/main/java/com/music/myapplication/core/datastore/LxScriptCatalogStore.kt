package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.music.myapplication.data.repository.lx.LxScriptCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.lxScriptCatalogDataStore by preferencesDataStore("lx_script_catalog")

@Singleton
class LxScriptCatalogStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private object Keys {
        val CATALOG = stringPreferencesKey("catalog")
    }

    val catalog: Flow<LxScriptCatalog> = context.lxScriptCatalogDataStore.data
        .map { prefs -> prefs[Keys.CATALOG].toCatalog() }
        .catch { emit(LxScriptCatalog()) }

    suspend fun read(): LxScriptCatalog =
        runCatching { context.lxScriptCatalogDataStore.data.first() }
            .getOrElse { emptyPreferences() }
            .let { prefs -> prefs[Keys.CATALOG].toCatalog() }

    suspend fun update(transform: (LxScriptCatalog) -> LxScriptCatalog) {
        context.lxScriptCatalogDataStore.edit { prefs ->
            val current = prefs[Keys.CATALOG].toCatalog()
            prefs[Keys.CATALOG] = json.encodeToString(transform(current))
        }
    }

    private fun String?.toCatalog(): LxScriptCatalog {
        val payload = this.orEmpty()
        if (payload.isBlank()) return LxScriptCatalog()
        return runCatching { json.decodeFromString<LxScriptCatalog>(payload) }
            .getOrDefault(LxScriptCatalog())
    }
}
