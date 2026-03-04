package com.music.myapplication.di

import android.content.Context
import androidx.room.Room
import com.music.myapplication.core.database.AppDatabase
import com.music.myapplication.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "music_db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFavoritesDao(db: AppDatabase): FavoritesDao = db.favoritesDao()

    @Provides
    fun provideRecentPlaysDao(db: AppDatabase): RecentPlaysDao = db.recentPlaysDao()

    @Provides
    fun providePlaylistsDao(db: AppDatabase): PlaylistsDao = db.playlistsDao()

    @Provides
    fun providePlaylistSongsDao(db: AppDatabase): PlaylistSongsDao = db.playlistSongsDao()

    @Provides
    fun provideLyricsCacheDao(db: AppDatabase): LyricsCacheDao = db.lyricsCacheDao()
}
