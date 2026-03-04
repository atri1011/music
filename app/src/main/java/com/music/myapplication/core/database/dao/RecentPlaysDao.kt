package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.myapplication.core.database.entity.RecentPlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPlaysDao {

    @Query("SELECT * FROM recent_plays ORDER BY played_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<RecentPlayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentPlayEntity)

    @Query("""
        INSERT OR REPLACE INTO recent_plays (song_id, platform, title, artist, cover_url, duration_ms, played_at, position_ms)
        VALUES (:songId, :platform, :title, :artist, :coverUrl, :durationMs, :playedAt, :positionMs)
    """)
    suspend fun insertOrUpdate(
        songId: String, platform: String, title: String, artist: String,
        coverUrl: String, durationMs: Long, playedAt: Long, positionMs: Long
    )

    @Query("DELETE FROM recent_plays WHERE id NOT IN (SELECT id FROM recent_plays ORDER BY played_at DESC LIMIT :keepCount)")
    suspend fun trimOldEntries(keepCount: Int = 100)

    @Query("DELETE FROM recent_plays")
    suspend fun clearAll()
}
