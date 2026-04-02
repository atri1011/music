package com.music.myapplication.media.service

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoSearchIndexTest {

    @Test
    fun `buildAndroidAutoSearchPage matches playlists and deduplicates tracks across sources`() {
        val sharedTrack = track(
            id = "song-1",
            title = "夜曲",
            artist = "周杰伦",
            album = "十一月的萧邦"
        )
        val page = buildAndroidAutoSearchPage(
            query = "周杰 夜曲",
            snapshot = AndroidAutoSearchSnapshot(
                favorites = listOf(sharedTrack),
                recents = listOf(sharedTrack.copy()),
                localTracks = listOf(sharedTrack.copy(platform = Platform.LOCAL)),
                playlists = listOf(
                    Playlist(id = "playlist-1", name = "夜曲合集", trackCount = 1),
                    Playlist(id = "playlist-2", name = "通勤歌单", trackCount = 1)
                ),
                playlistTracks = mapOf(
                    "playlist-1" to listOf(sharedTrack.copy()),
                    "playlist-2" to listOf(track(id = "song-2", title = "稻香", artist = "周杰伦"))
                )
            ),
            page = 0,
            pageSize = 20
        )

        assertEquals(3, page.totalCount)
        assertEquals(3, page.entries.size)
        assertTrue(page.entries[0] is AndroidAutoSearchEntry.PlaylistEntry)
        assertEquals("playlist-1", (page.entries[0] as AndroidAutoSearchEntry.PlaylistEntry).playlist.id)

        val trackEntries = page.entries.filterIsInstance<AndroidAutoSearchEntry.TrackEntry>()
        assertEquals(2, trackEntries.size)
        assertEquals(listOf("qq:song-1", "local:song-1"), trackEntries.map { "${it.track.platform.id}:${it.track.id}" })
    }

    @Test
    fun `buildAndroidAutoSearchPage slices results by page and pageSize`() {
        val tracks = (1..5).map { index ->
            track(
                id = "song-$index",
                title = "晚风第${index}首",
                artist = "测试歌手"
            )
        }
        val page = buildAndroidAutoSearchPage(
            query = "晚风",
            snapshot = AndroidAutoSearchSnapshot(
                playlists = emptyList(),
                favorites = tracks,
                recents = emptyList(),
                localTracks = emptyList(),
                playlistTracks = emptyMap()
            ),
            page = 1,
            pageSize = 2
        )

        val trackEntries = page.entries.filterIsInstance<AndroidAutoSearchEntry.TrackEntry>()
        assertEquals(5, page.totalCount)
        assertEquals(listOf("song-3", "song-4"), trackEntries.map { it.track.id })
    }

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String = ""
    ) = Track(
        id = id,
        platform = Platform.QQ,
        title = title,
        artist = artist,
        album = album
    )
}
