package com.music.myapplication.media.service

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track

internal const val ROOT_ID = "library_root"
internal const val LIBRARY_ID = "library"
internal const val FAVORITES_ID = "favorites"
internal const val RECENTS_ID = "recent_plays"
internal const val TOP_PLAYED_ID = "top_played"
internal const val LOCAL_TRACKS_ID = "local_tracks"
internal const val PLAYLISTS_ID = "playlists"
internal const val PLAYLIST_PREFIX = "playlist:"
internal const val TRACK_PREFIX = "track:"

internal data class AndroidAutoRootContentStyleHints(
    val browsable: Int,
    val playable: Int
)

internal data class AndroidAutoBrowseSnapshot(
    val favorites: List<Track> = emptyList(),
    val recents: List<Track> = emptyList(),
    val topPlayed: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val playlistTracks: Map<String, List<Track>> = emptyMap(),
    val favoriteCount: Int = favorites.size,
    val recentCount: Int = recents.size,
    val topPlayedCount: Int = topPlayed.size,
    val localTrackCount: Int = localTracks.size,
    val playlistCount: Int = playlists.size
)

internal sealed interface AndroidAutoBrowseEntry {
    data class FolderEntry(
        val mediaId: String,
        val title: String,
        val subtitle: String,
        val mediaType: Int,
        val artworkUrl: String = "",
        val groupTitle: String? = null
    ) : AndroidAutoBrowseEntry

    data class TrackEntry(val track: Track) : AndroidAutoBrowseEntry
}

internal fun buildAndroidAutoRootContentStyleHints(): AndroidAutoRootContentStyleHints =
    AndroidAutoRootContentStyleHints(
        browsable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        playable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    )

internal fun buildAndroidAutoRootLibraryParams(
    requestParams: MediaLibraryService.LibraryParams? = null
): MediaLibraryService.LibraryParams {
    val contentStyleHints = buildAndroidAutoRootContentStyleHints()
    val extras = Bundle().apply {
        putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            contentStyleHints.browsable
        )
        putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            contentStyleHints.playable
        )
    }
    return MediaLibraryService.LibraryParams.Builder()
        .setOffline(requestParams?.isOffline ?: false)
        .setRecent(requestParams?.isRecent ?: false)
        .setSuggested(requestParams?.isSuggested ?: false)
        .setExtras(extras)
        .build()
}

internal fun buildAndroidAutoBrowseFolderExtras(entry: AndroidAutoBrowseEntry.FolderEntry): Bundle? {
    val groupTitle = entry.groupTitle ?: return null
    return Bundle().apply {
        putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle)
    }
}

internal fun buildAndroidAutoBrowseEntries(
    parentId: String,
    snapshot: AndroidAutoBrowseSnapshot
): List<AndroidAutoBrowseEntry> = when {
    parentId == ROOT_ID -> buildRootEntries(snapshot)
    parentId == LIBRARY_ID -> buildLibraryEntries(snapshot)
    parentId == FAVORITES_ID -> snapshot.favorites.map(AndroidAutoBrowseEntry::TrackEntry)
    parentId == RECENTS_ID -> snapshot.recents.map(AndroidAutoBrowseEntry::TrackEntry)
    parentId == TOP_PLAYED_ID -> snapshot.topPlayed.map(AndroidAutoBrowseEntry::TrackEntry)
    parentId == LOCAL_TRACKS_ID -> snapshot.localTracks.map(AndroidAutoBrowseEntry::TrackEntry)
    parentId == PLAYLISTS_ID -> snapshot.playlists.map(::buildPlaylistFolderEntry)
    parentId.startsWith(PLAYLIST_PREFIX) -> {
        val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
        snapshot.playlistTracks[playlistId].orEmpty().map(AndroidAutoBrowseEntry::TrackEntry)
    }
    else -> emptyList()
}

private fun buildRootEntries(snapshot: AndroidAutoBrowseSnapshot): List<AndroidAutoBrowseEntry.FolderEntry> {
    return listOf(
        AndroidAutoBrowseEntry.FolderEntry(
            mediaId = RECENTS_ID,
            title = "最近播放",
            subtitle = if (snapshot.recentCount > 0) {
                "${snapshot.recentCount} 首，接着上次继续听"
            } else {
                "从最近播放里继续"
            },
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            groupTitle = "快速访问"
        ),
        AndroidAutoBrowseEntry.FolderEntry(
            mediaId = TOP_PLAYED_ID,
            title = "最常播放",
            subtitle = if (snapshot.topPlayedCount > 0) {
                "${snapshot.topPlayedCount} 首高频歌曲"
            } else {
                "按播放次数整理你的常听歌曲"
            },
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            groupTitle = "快速访问"
        ),
        AndroidAutoBrowseEntry.FolderEntry(
            mediaId = LIBRARY_ID,
            title = "资料库",
            subtitle = buildLibrarySummarySubtitle(snapshot),
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            groupTitle = "资料库"
        )
    )
}

private fun buildLibraryEntries(snapshot: AndroidAutoBrowseSnapshot): List<AndroidAutoBrowseEntry.FolderEntry> {
    return listOf(
        AndroidAutoBrowseEntry.FolderEntry(
            mediaId = LOCAL_TRACKS_ID,
            title = "本地歌曲",
            subtitle = if (snapshot.localTrackCount > 0) {
                "${snapshot.localTrackCount} 首设备中的歌曲"
            } else {
                "浏览设备中的歌曲"
            },
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        ),
        AndroidAutoBrowseEntry.FolderEntry(
            mediaId = FAVORITES_ID,
            title = "收藏",
            subtitle = if (snapshot.favoriteCount > 0) {
                "${snapshot.favoriteCount} 首已收藏"
            } else {
                "浏览收藏的歌曲"
            },
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        ),
        AndroidAutoBrowseEntry.FolderEntry(
            mediaId = PLAYLISTS_ID,
            title = "歌单",
            subtitle = if (snapshot.playlistCount > 0) {
                "${snapshot.playlistCount} 个自建歌单"
            } else {
                "浏览自建歌单"
            },
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        )
    )
}

private fun buildPlaylistFolderEntry(playlist: Playlist): AndroidAutoBrowseEntry.FolderEntry =
    AndroidAutoBrowseEntry.FolderEntry(
        mediaId = "$PLAYLIST_PREFIX${playlist.id}",
        title = playlist.name,
        subtitle = "${playlist.trackCount} 首",
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        artworkUrl = playlist.coverUrl
    )

private fun buildLibrarySummarySubtitle(snapshot: AndroidAutoBrowseSnapshot): String {
    return if (
        snapshot.localTrackCount == 0 &&
        snapshot.favoriteCount == 0 &&
        snapshot.playlistCount == 0
    ) {
        "本地歌曲、收藏和歌单"
    } else {
        "本地 ${snapshot.localTrackCount} 首 / 收藏 ${snapshot.favoriteCount} 首 / 歌单 ${snapshot.playlistCount} 个"
    }
}
