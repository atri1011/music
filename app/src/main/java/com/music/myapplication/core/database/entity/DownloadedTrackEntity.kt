package com.music.myapplication.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "downloaded_tracks",
    primaryKeys = ["song_id", "platform"],
    indices = [
        Index(value = ["download_status"], name = "idx_downloaded_status"),
        Index(value = ["downloaded_at"], name = "idx_downloaded_at")
    ]
)
data class DownloadedTrackEntity(
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String = "",
    @ColumnInfo(name = "cover_url") val coverUrl: String = "",
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "file_path") val filePath: String = "",
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "quality") val quality: String = "128k",
    @ColumnInfo(name = "progress_percent") val progressPercent: Int = 0,
    @ColumnInfo(name = "failure_reason") val failureReason: String = "",
    @ColumnInfo(name = "request_id") val requestId: String = "",
    @ColumnInfo(name = "download_status") val downloadStatus: String = DownloadStatus.DOWNLOADING,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long = System.currentTimeMillis()
) {
    object DownloadStatus {
        const val DOWNLOADING = "downloading"
        const val SUCCESS = "success"
        const val FAILED = "failed"
    }
}
