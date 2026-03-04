package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "favorites",
    primaryKeys = ["song_id", "platform"]
)
data class FavoriteEntity(
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String = "",
    @ColumnInfo(name = "cover_url") val coverUrl: String = "",
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
