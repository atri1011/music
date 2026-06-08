package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.music.myapplication.core.database.entity.PlaylistFolderEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistFolderSummaryRow(
    val folderId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val playlistCount: Int
)

@Dao
interface PlaylistFoldersDao {

    @Query(
        """
        SELECT
            f.folder_id AS folderId,
            f.name AS name,
            f.created_at AS createdAt,
            f.updated_at AS updatedAt,
            COUNT(p.playlist_id) AS playlistCount
        FROM playlist_folders f
        LEFT JOIN playlists p ON p.folder_id = f.folder_id
        GROUP BY f.folder_id
        ORDER BY f.updated_at DESC
        """
    )
    fun getAllWithStats(): Flow<List<PlaylistFolderSummaryRow>>

    @Query("SELECT * FROM playlist_folders WHERE folder_id = :folderId")
    suspend fun getById(folderId: String): PlaylistFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistFolderEntity)

    @Update
    suspend fun update(entity: PlaylistFolderEntity)

    @Query("DELETE FROM playlist_folders WHERE folder_id = :folderId")
    suspend fun delete(folderId: String)
}
