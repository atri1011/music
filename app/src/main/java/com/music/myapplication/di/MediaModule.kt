package com.music.myapplication.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.music.myapplication.media.playback.CachedPlaybackMediaSourceFactoryDelegate
import com.music.myapplication.media.playback.DirectPlaybackMediaSourceFactoryDelegate
import com.music.myapplication.media.playback.PLAYBACK_USER_AGENT
import com.music.myapplication.media.playback.buildPlaybackRequestHeaders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    private const val PLAYBACK_CACHE_DIR_NAME = "playback_stream_cache"
    private const val PLAYBACK_CACHE_MAX_BYTES = 128L * 1024L * 1024L

    @UnstableApi
    @Provides
    @Singleton
    fun providePlaybackSimpleCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, PLAYBACK_CACHE_DIR_NAME),
            LeastRecentlyUsedCacheEvictor(PLAYBACK_CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context)
        )

    @UnstableApi
    @Provides
    @Singleton
    @CachedPlaybackMediaSourceFactoryDelegate
    fun provideCachedPlaybackMediaSourceFactory(
        @ApplicationContext context: Context,
        playbackCache: SimpleCache
    ): MediaSource.Factory {
        val resolvingHttpFactory = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setUserAgent(PLAYBACK_USER_AGENT)
        ) { dataSpec ->
            val resolvedHeaders = dataSpec.httpRequestHeaders + buildPlaybackRequestHeaders(
                dataSpec.uri.toString()
            )
            dataSpec.withRequestHeaders(resolvedHeaders)
        }
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(playbackCache)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(playbackCache))
            .setUpstreamDataSourceFactory(resolvingHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory)
    }

    @Provides
    @Singleton
    @DirectPlaybackMediaSourceFactoryDelegate
    fun provideDirectPlaybackMediaSourceFactory(
        @ApplicationContext context: Context
    ): MediaSource.Factory = DefaultMediaSourceFactory(context)

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
}
