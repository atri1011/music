package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_tracks",
    indices = [
        Index(value = ["artist"], name = "idx_local_tracks_artist"),
        Index(value = ["album"], name = "idx_local_tracks_album")
    ]
)
data class LocalTrackEntity(
    @PrimaryKey @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String = "",
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "file_path") val filePath: String = "",
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "mime_type") val mimeType: String = "",
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)
