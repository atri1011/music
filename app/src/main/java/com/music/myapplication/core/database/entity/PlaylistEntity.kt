package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["updated_at"], name = "idx_playlists_updated_at"),
        Index(value = ["folder_id"], name = "idx_playlists_folder")
    ]
)
data class PlaylistEntity(
    @PrimaryKey @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String = "",
    @ColumnInfo(name = "folder_id") val folderId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
