package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTracksDao {

    @Query("SELECT * FROM downloaded_tracks WHERE download_status = 'success' ORDER BY downloaded_at DESC")
    fun getDownloaded(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloaded_at DESC")
    fun getAll(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE download_status = 'downloading'")
    fun getDownloading(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE download_status = 'downloading'")
    suspend fun getDownloadingNow(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform LIMIT 1")
    suspend fun get(songId: String, platform: String): DownloadedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform AND download_status = 'success')")
    suspend fun isDownloaded(songId: String, platform: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedTrackEntity)

    @Update
    suspend fun update(entity: DownloadedTrackEntity)

    @Query("UPDATE downloaded_tracks SET download_status = :status WHERE song_id = :songId AND platform = :platform")
    suspend fun updateStatus(songId: String, platform: String, status: String)

    @Query("UPDATE downloaded_tracks SET download_status = :status, file_path = :filePath, file_size_bytes = :fileSize WHERE song_id = :songId AND platform = :platform")
    suspend fun updateDownloadComplete(
        songId: String, platform: String, status: String, filePath: String, fileSize: Long
    )

    @Query("DELETE FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform")
    suspend fun delete(songId: String, platform: String)

    @Query("DELETE FROM downloaded_tracks WHERE download_status = 'failed'")
    suspend fun clearFailed()

    @Query("SELECT COUNT(*) FROM downloaded_tracks WHERE download_status = 'success'")
    fun countDownloaded(): Flow<Int>
}
