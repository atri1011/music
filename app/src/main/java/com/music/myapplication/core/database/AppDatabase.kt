package com.music.myapplication.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.music.myapplication.core.database.dao.*
import com.music.myapplication.core.database.entity.*

@Database(
    entities = [
        FavoriteEntity::class,
        RecentPlayEntity::class,
        PlaylistEntity::class,
        PlaylistRemoteMapEntity::class,
        PlaylistSongEntity::class,
        LyricsCacheEntity::class,
        DownloadedTrackEntity::class,
        LocalTrackEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun recentPlaysDao(): RecentPlaysDao
    abstract fun playlistsDao(): PlaylistsDao
    abstract fun playlistRemoteMapDao(): PlaylistRemoteMapDao
    abstract fun playlistSongsDao(): PlaylistSongsDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun downloadedTracksDao(): DownloadedTracksDao
    abstract fun localTracksDao(): LocalTracksDao
}
