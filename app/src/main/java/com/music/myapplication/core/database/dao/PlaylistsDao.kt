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
            p.created_at AS createdAt,
            p.updated_at AS updatedAt,
            COUNT(ps.song_id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlist_id = p.playlist_id
        GROUP BY p.playlist_id
        ORDER BY p.updated_at DESC
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
}
