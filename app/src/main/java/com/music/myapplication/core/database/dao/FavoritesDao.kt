package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.myapplication.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites ORDER BY added_at DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE song_id = :songId AND platform = :platform)")
    suspend fun isFavorite(songId: String, platform: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE song_id = :songId AND platform = :platform")
    suspend fun delete(songId: String, platform: String)

    @Query("SELECT COUNT(*) FROM favorites")
    fun count(): Flow<Int>
}
