package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_remote_map",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlist_id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["source_platform", "source_playlist_id", "owner_uid"],
            unique = true,
            name = "idx_playlist_remote_map_source_unique"
        )
    ]
)
data class PlaylistRemoteMapEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "source_platform")
    val sourcePlatform: String,
    @ColumnInfo(name = "source_playlist_id")
    val sourcePlaylistId: String,
    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)
