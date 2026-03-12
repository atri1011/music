package com.music.myapplication.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.myapplication.core.database.entity.PlaylistRemoteMapEntity

@Dao
interface PlaylistRemoteMapDao {

    @Query(
        """
        SELECT * FROM playlist_remote_map
        WHERE source_platform = :sourcePlatform
          AND source_playlist_id = :sourcePlaylistId
          AND owner_uid = :ownerUid
        LIMIT 1
        """
    )
    suspend fun getByRemoteSource(
        sourcePlatform: String,
        sourcePlaylistId: String,
        ownerUid: String
    ): PlaylistRemoteMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistRemoteMapEntity)
}
