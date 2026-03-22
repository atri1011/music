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

    @Query(
        """
        SELECT * FROM downloaded_tracks
        ORDER BY
            CASE download_status
                WHEN 'downloading' THEN 0
                WHEN 'failed' THEN 1
                ELSE 2
            END,
            downloaded_at DESC
        """
    )
    fun getAll(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE download_status = 'downloading' ORDER BY downloaded_at DESC")
    fun getDownloading(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE download_status = 'downloading' ORDER BY downloaded_at DESC")
    suspend fun getDownloadingNow(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE download_status = 'success' ORDER BY downloaded_at DESC")
    suspend fun getDownloadedNow(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform LIMIT 1")
    suspend fun get(songId: String, platform: String): DownloadedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform AND download_status = 'success')")
    suspend fun isDownloaded(songId: String, platform: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedTrackEntity)

    @Update
    suspend fun update(entity: DownloadedTrackEntity)

    @Query(
        """
        UPDATE downloaded_tracks
        SET download_status = :status,
            progress_percent = :progressPercent,
            failure_reason = :failureReason,
            request_id = ''
        WHERE song_id = :songId AND platform = :platform AND request_id = :requestId
        """
    )
    suspend fun updateState(
        songId: String,
        platform: String,
        requestId: String,
        status: String,
        progressPercent: Int,
        failureReason: String
    ): Int

    @Query(
        """
        UPDATE downloaded_tracks
        SET progress_percent = :progressPercent
        WHERE song_id = :songId AND platform = :platform AND request_id = :requestId
        """
    )
    suspend fun updateProgress(songId: String, platform: String, requestId: String, progressPercent: Int): Int

    @Query(
        """
        UPDATE downloaded_tracks
        SET download_status = :status,
            file_path = :filePath,
            file_size_bytes = :fileSize,
            progress_percent = 100,
            failure_reason = '',
            request_id = '',
            downloaded_at = :downloadedAt
        WHERE song_id = :songId AND platform = :platform AND request_id = :requestId
        """
    )
    suspend fun updateDownloadComplete(
        songId: String,
        platform: String,
        requestId: String,
        status: String,
        filePath: String,
        fileSize: Long,
        downloadedAt: Long
    ): Int

    @Query(
        """
        UPDATE downloaded_tracks
        SET download_status = 'failed',
            progress_percent = :progressPercent,
            failure_reason = :failureReason,
            request_id = '',
            file_path = '',
            file_size_bytes = 0
        WHERE song_id = :songId AND platform = :platform AND request_id = :requestId
        """
    )
    suspend fun markFailed(
        songId: String,
        platform: String,
        requestId: String,
        failureReason: String,
        progressPercent: Int = 0
    ): Int

    @Query("DELETE FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform AND request_id = :requestId")
    suspend fun delete(songId: String, platform: String, requestId: String): Int

    @Query("DELETE FROM downloaded_tracks WHERE song_id = :songId AND platform = :platform")
    suspend fun deleteBySongIdAndPlatform(songId: String, platform: String)

    @Query("DELETE FROM downloaded_tracks WHERE download_status = 'failed'")
    suspend fun clearFailed()

    @Query("SELECT COUNT(*) FROM downloaded_tracks WHERE download_status = 'success'")
    fun countDownloaded(): Flow<Int>
}
