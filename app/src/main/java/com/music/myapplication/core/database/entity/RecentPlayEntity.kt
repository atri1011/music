package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_plays",
    indices = [
        Index(value = ["played_at"], name = "idx_recent_played_at"),
        Index(value = ["song_id", "platform"], unique = true)
    ]
)
data class RecentPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String = "",
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "played_at") val playedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0L,
    @ColumnInfo(name = "play_count", defaultValue = "1") val playCount: Int = 1
)
