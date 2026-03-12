package com.music.myapplication.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.music.myapplication.domain.model.NeteaseAccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.neteaseAccountDataStore by preferencesDataStore("netease_account_store")

@Serializable
private data class PersistedNeteaseAccountSession(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String = "",
    val cookie: String,
    val lastLoginAt: Long,
    val lastSyncAt: Long = 0L
)

@Singleton
class NeteaseAccountStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SESSION = stringPreferencesKey("netease_account_session")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val session: Flow<NeteaseAccountSession?> = context.neteaseAccountDataStore.data.map { prefs ->
        prefs[Keys.SESSION]
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeSession)
    }

    suspend fun saveSession(session: NeteaseAccountSession) {
        context.neteaseAccountDataStore.edit { prefs ->
            prefs[Keys.SESSION] = json.encodeToString(session.toPersisted())
        }
    }

    suspend fun clearSession() {
        context.neteaseAccountDataStore.edit { prefs ->
            prefs.remove(Keys.SESSION)
        }
    }

    private fun decodeSession(payload: String): NeteaseAccountSession? =
        runCatching { json.decodeFromString<PersistedNeteaseAccountSession>(payload).toDomain() }
            .getOrNull()

    private fun NeteaseAccountSession.toPersisted(): PersistedNeteaseAccountSession =
        PersistedNeteaseAccountSession(
            userId = userId,
            nickname = nickname,
            avatarUrl = avatarUrl,
            cookie = cookie,
            lastLoginAt = lastLoginAt,
            lastSyncAt = lastSyncAt
        )

    private fun PersistedNeteaseAccountSession.toDomain(): NeteaseAccountSession =
        NeteaseAccountSession(
            userId = userId,
            nickname = nickname,
            avatarUrl = avatarUrl,
            cookie = cookie,
            lastLoginAt = lastLoginAt,
            lastSyncAt = lastSyncAt
        )
}
