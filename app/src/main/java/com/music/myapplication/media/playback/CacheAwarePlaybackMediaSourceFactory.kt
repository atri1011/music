package com.music.myapplication.media.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MediaSource
import com.music.myapplication.domain.model.Track
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CachedPlaybackMediaSourceFactoryDelegate

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DirectPlaybackMediaSourceFactoryDelegate

@Singleton
class CacheAwarePlaybackMediaSourceFactory @Inject constructor(
    @CachedPlaybackMediaSourceFactoryDelegate private val cachedFactory: MediaSource.Factory,
    @DirectPlaybackMediaSourceFactoryDelegate private val directFactory: MediaSource.Factory
) {
    fun create(track: Track, mediaItem: MediaItem): MediaSource =
        if (shouldUsePlaybackCache(track.playableUrl)) {
            cachedFactory.createMediaSource(mediaItem)
        } else {
            directFactory.createMediaSource(mediaItem)
        }
}
