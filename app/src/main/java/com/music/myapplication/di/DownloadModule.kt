package com.music.myapplication.di

import com.music.myapplication.core.database.dao.DownloadedTracksDao
import com.music.myapplication.core.download.DownloadManager
import android.content.Context
import com.music.myapplication.core.datastore.PlayerPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.music.myapplication.core.network.NetworkMonitor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        downloadedTracksDao: DownloadedTracksDao,
        preferences: PlayerPreferences,
        networkMonitor: NetworkMonitor
    ): DownloadManager = DownloadManager(context, downloadedTracksDao, preferences, networkMonitor)
}
