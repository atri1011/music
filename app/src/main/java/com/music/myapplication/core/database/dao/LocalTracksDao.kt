package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.myapplication.core.database.entity.LocalTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalTracksDao {

    @Query("SELECT * FROM local_tracks ORDER BY title ASC")
    fun getAll(): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_tracks WHERE media_store_id = :mediaStoreId LIMIT 1")
    suspend fun get(mediaStoreId: Long): LocalTrackEntity?

    @Query("SELECT * FROM local_tracks")
    suspend fun getAllOnce(): List<LocalTrackEntity>

    @Query("SELECT media_store_id FROM local_tracks")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LocalTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(entity: LocalTrackEntity)

    @Query("DELETE FROM local_tracks WHERE media_store_id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM local_tracks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM local_tracks")
    fun count(): Flow<Int>

    @Query("SELECT * FROM local_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title ASC")
    fun search(query: String): Flow<List<LocalTrackEntity>>
}
