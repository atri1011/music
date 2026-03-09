package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.myapplication.core.database.entity.LyricsCacheEntity

@Dao
interface LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE cache_key = :key AND expires_at > :now")
    suspend fun get(key: String, now: Long = System.currentTimeMillis()): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE expires_at < :now")
    suspend fun cleanExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(SUM(LENGTH(lyric_text)), 0) FROM lyrics_cache WHERE expires_at > :now")
    suspend fun getActiveSizeBytes(now: Long = System.currentTimeMillis()): Long

    @Query("DELETE FROM lyrics_cache")
    suspend fun deleteAll()
}
