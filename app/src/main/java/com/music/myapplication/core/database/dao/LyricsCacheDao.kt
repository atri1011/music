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

    @Query("SELECT COALESCE(SUM(LENGTH(CAST(lyric_text AS BLOB))), 0) FROM lyrics_cache WHERE expires_at > :now")
    suspend fun getActiveSizeBytes(now: Long = System.currentTimeMillis()): Long

    @Query("SELECT * FROM lyrics_cache WHERE expires_at > :now ORDER BY updated_at ASC")
    suspend fun getActiveEntriesOrderedByUpdatedAt(now: Long = System.currentTimeMillis()): List<LyricsCacheEntity>

    @Query("DELETE FROM lyrics_cache WHERE cache_key IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("DELETE FROM lyrics_cache")
    suspend fun deleteAll()
}
