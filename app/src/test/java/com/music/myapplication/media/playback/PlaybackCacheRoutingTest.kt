package com.music.myapplication.media.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MediaSource
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackCacheRoutingTest {

    @Test
    fun `remote http playback uses cached media source factory`() {
        val cachedFactory = mockk<MediaSource.Factory>()
        val directFactory = mockk<MediaSource.Factory>()
        val cachedMediaSource = mockk<MediaSource>()
        val mediaItem = mockk<MediaItem>()
        val track = remoteTrack("https://cdn.example.com/song.mp3")

        every { cachedFactory.createMediaSource(mediaItem) } returns cachedMediaSource

        val factory = CacheAwarePlaybackMediaSourceFactory(cachedFactory, directFactory)

        val result = factory.create(track, mediaItem)

        assertSame(cachedMediaSource, result)
        verify(exactly = 1) { cachedFactory.createMediaSource(mediaItem) }
        verify(exactly = 0) { directFactory.createMediaSource(any()) }
    }

    @Test
    fun `content uri playback stays on direct media source factory`() {
        val cachedFactory = mockk<MediaSource.Factory>()
        val directFactory = mockk<MediaSource.Factory>()
        val directMediaSource = mockk<MediaSource>()
        val mediaItem = mockk<MediaItem>()
        val track = remoteTrack("content://media/external/audio/media/42")

        every { directFactory.createMediaSource(mediaItem) } returns directMediaSource

        val factory = CacheAwarePlaybackMediaSourceFactory(cachedFactory, directFactory)

        val result = factory.create(track, mediaItem)

        assertSame(directMediaSource, result)
        verify(exactly = 0) { cachedFactory.createMediaSource(any()) }
        verify(exactly = 1) { directFactory.createMediaSource(mediaItem) }
    }

    @Test
    fun `invalid http uri without host stays on direct media source factory`() {
        val cachedFactory = mockk<MediaSource.Factory>()
        val directFactory = mockk<MediaSource.Factory>()
        val directMediaSource = mockk<MediaSource>()
        val mediaItem = mockk<MediaItem>()
        val track = remoteTrack("https://")

        every { directFactory.createMediaSource(mediaItem) } returns directMediaSource

        val factory = CacheAwarePlaybackMediaSourceFactory(cachedFactory, directFactory)

        val result = factory.create(track, mediaItem)

        assertSame(directMediaSource, result)
        verify(exactly = 0) { cachedFactory.createMediaSource(any()) }
        verify(exactly = 1) { directFactory.createMediaSource(mediaItem) }
    }

    @Test
    fun `quoted remote http playback still uses cached media source factory`() {
        val cachedFactory = mockk<MediaSource.Factory>()
        val directFactory = mockk<MediaSource.Factory>()
        val cachedMediaSource = mockk<MediaSource>()
        val mediaItem = mockk<MediaItem>()
        val track = remoteTrack("  'https://cdn.example.com/song.mp3'  ")

        every { cachedFactory.createMediaSource(mediaItem) } returns cachedMediaSource

        val factory = CacheAwarePlaybackMediaSourceFactory(cachedFactory, directFactory)

        val result = factory.create(track, mediaItem)

        assertSame(cachedMediaSource, result)
        verify(exactly = 1) { cachedFactory.createMediaSource(mediaItem) }
        verify(exactly = 0) { directFactory.createMediaSource(any()) }
    }

    private fun remoteTrack(playableUrl: String) = Track(
        id = "song-1",
        platform = Platform.QQ,
        title = "晴天",
        artist = "周杰伦",
        playableUrl = playableUrl
    )
}
