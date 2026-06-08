package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.myapplication.core.database.entity.PlaybackEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackEventsDao {

    @Query("SELECT * FROM playback_events ORDER BY played_at DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 500): Flow<List<PlaybackEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PlaybackEventEntity)

    @Query("DELETE FROM playback_events WHERE id NOT IN (SELECT id FROM playback_events ORDER BY played_at DESC LIMIT :keepCount)")
    suspend fun trimOldEvents(keepCount: Int = 5000)
}
