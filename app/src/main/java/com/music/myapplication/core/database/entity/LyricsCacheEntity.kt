package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics_cache",
    indices = [Index(value = ["expires_at"], name = "idx_lyrics_expires_at")]
)
data class LyricsCacheEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "lyric_text") val lyricText: String,
    @ColumnInfo(name = "source") val source: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at") val expiresAt: Long = System.currentTimeMillis() + 7 * 24 * 3600 * 1000L
)
