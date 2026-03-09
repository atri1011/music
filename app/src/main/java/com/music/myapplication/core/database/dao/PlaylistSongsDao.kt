package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.music.myapplication.core.database.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSongsDao {

    @Query("SELECT * FROM playlist_songs WHERE playlist_id = :playlistId ORDER BY order_in_playlist ASC")
    fun getSongsByPlaylist(playlistId: String): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId AND platform = :platform")
    suspend fun delete(playlistId: String, songId: String, platform: String)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun deleteAllByPlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun countByPlaylist(playlistId: String): Int

    @Query("SELECT MAX(order_in_playlist) FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun getMaxOrder(playlistId: String): Int?

    @Transaction
    suspend fun replacePlaylistSongs(playlistId: String, entities: List<PlaylistSongEntity>) {
        deleteAllByPlaylist(playlistId)
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}
