package com.music.myapplication.media.service

import androidx.media3.session.MediaConstants
import androidx.media3.common.MediaMetadata
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoBrowseTreeTest {

    @Test
    fun `buildAndroidAutoRootContentStyleHints prefer list items for browsable and playable rows`() {
        val hints = buildAndroidAutoRootContentStyleHints()

        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            hints.browsable
        )
        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            hints.playable
        )
    }

    @Test
    fun `buildAndroidAutoBrowseEntries exposes focused root categories with library summary`() {
        val entries = buildAndroidAutoBrowseEntries(
            parentId = ROOT_ID,
            snapshot = AndroidAutoBrowseSnapshot(
                recentCount = 5,
                topPlayedCount = 8,
                favoriteCount = 12,
                localTrackCount = 41,
                playlistCount = 3
            )
        )

        assertEquals(3, entries.size)
        assertEquals(
            listOf(RECENTS_ID, TOP_PLAYED_ID, LIBRARY_ID),
            entries.map { (it as AndroidAutoBrowseEntry.FolderEntry).mediaId }
        )
        assertEquals("资料库", (entries[2] as AndroidAutoBrowseEntry.FolderEntry).title)
        assertEquals(
            "本地 41 首 / 收藏 12 首 / 歌单 3 个",
            (entries[2] as AndroidAutoBrowseEntry.FolderEntry).subtitle
        )
        assertEquals(
            listOf("快速访问", "快速访问", "资料库"),
            entries.map { (it as AndroidAutoBrowseEntry.FolderEntry).groupTitle }
        )
    }

    @Test
    fun `buildAndroidAutoBrowseEntries exposes explicit library surfaces for local music favorites and playlists`() {
        val entries = buildAndroidAutoBrowseEntries(
            parentId = LIBRARY_ID,
            snapshot = AndroidAutoBrowseSnapshot(
                favoriteCount = 12,
                localTrackCount = 41,
                playlistCount = 3
            )
        )

        assertEquals(
            listOf(LOCAL_TRACKS_ID, FAVORITES_ID, PLAYLISTS_ID),
            entries.map { (it as AndroidAutoBrowseEntry.FolderEntry).mediaId }
        )
        assertEquals(
            listOf(
                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            ),
            entries.map { (it as AndroidAutoBrowseEntry.FolderEntry).mediaType }
        )
        assertEquals("41 首设备中的歌曲", (entries[0] as AndroidAutoBrowseEntry.FolderEntry).subtitle)
        assertEquals("12 首已收藏", (entries[1] as AndroidAutoBrowseEntry.FolderEntry).subtitle)
        assertEquals("3 个自建歌单", (entries[2] as AndroidAutoBrowseEntry.FolderEntry).subtitle)
    }

    @Test
    fun `buildAndroidAutoBrowseEntries returns track leaves for top played local tracks and playlists`() {
        val topPlayedTrack = track(id = "top-1", title = "晴天")
        val localTrack = track(
            id = "local-1",
            platform = Platform.LOCAL,
            title = "七里香"
        )
        val playlistTrack = track(id = "playlist-1-track", title = "稻香")
        val snapshot = AndroidAutoBrowseSnapshot(
            topPlayed = listOf(topPlayedTrack),
            localTracks = listOf(localTrack),
            playlists = listOf(Playlist(id = "playlist-1", name = "通勤歌单", trackCount = 1)),
            playlistTracks = mapOf("playlist-1" to listOf(playlistTrack))
        )

        val topPlayedEntries = buildAndroidAutoBrowseEntries(TOP_PLAYED_ID, snapshot)
        val localTrackEntries = buildAndroidAutoBrowseEntries(LOCAL_TRACKS_ID, snapshot)
        val playlistEntries = buildAndroidAutoBrowseEntries("$PLAYLIST_PREFIX${"playlist-1"}", snapshot)

        assertEquals("top-1", (topPlayedEntries.single() as AndroidAutoBrowseEntry.TrackEntry).track.id)
        assertEquals("local-1", (localTrackEntries.single() as AndroidAutoBrowseEntry.TrackEntry).track.id)
        assertTrue(playlistEntries.single() is AndroidAutoBrowseEntry.TrackEntry)
        assertEquals(
            "playlist-1-track",
            (playlistEntries.single() as AndroidAutoBrowseEntry.TrackEntry).track.id
        )
    }

    private fun track(
        id: String,
        title: String,
        platform: Platform = Platform.QQ
    ) = Track(
        id = id,
        platform = platform,
        title = title,
        artist = "周杰伦",
        album = "测试专辑"
    )
}
