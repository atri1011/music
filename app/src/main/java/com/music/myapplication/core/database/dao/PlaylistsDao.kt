package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.music.myapplication.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistSummaryRow(
    val playlistId: String,
    val name: String,
    val coverUrl: String,
    val folderId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int
)

@Dao
interface PlaylistsDao {

    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    fun getAll(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT
            p.playlist_id AS playlistId,
            p.name AS name,
            COALESCE(NULLIF(p.cover_url, ''), MAX(NULLIF(ps.cover_url, '')), '') AS coverUrl,
            p.folder_id AS folderId,
            p.created_at AS createdAt,
            p.updated_at AS updatedAt,
            COUNT(ps.song_id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlist_id = p.playlist_id
        LEFT JOIN playlist_remote_map prm ON prm.playlist_id = p.playlist_id
        GROUP BY p.playlist_id
        ORDER BY
            CASE WHEN MIN(prm.remote_order) IS NULL THEN 1 ELSE 0 END ASC,
            MIN(prm.remote_order) ASC,
            p.updated_at DESC
        """
    )
    fun getAllWithStats(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    suspend fun getById(playlistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistEntity)

    @Update
    suspend fun update(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlist_id = :playlistId")
    suspend fun delete(playlistId: String)

    @Query("UPDATE playlists SET folder_id = :folderId, updated_at = :updatedAt WHERE playlist_id = :playlistId")
    suspend fun updateFolder(playlistId: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE playlists SET folder_id = NULL, updated_at = :updatedAt WHERE folder_id = :folderId")
    suspend fun clearFolder(folderId: String, updatedAt: Long)
}
