package com.music.myapplication.media.service

import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import java.util.Locale

internal data class AndroidAutoSearchSnapshot(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val recents: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val playlistTracks: Map<String, List<Track>> = emptyMap()
)

internal sealed interface AndroidAutoSearchEntry {
    data class PlaylistEntry(val playlist: Playlist) : AndroidAutoSearchEntry
    data class TrackEntry(val track: Track) : AndroidAutoSearchEntry
}

internal data class AndroidAutoSearchPage(
    val entries: List<AndroidAutoSearchEntry>,
    val totalCount: Int
)

internal fun buildAndroidAutoSearchPage(
    query: String,
    snapshot: AndroidAutoSearchSnapshot,
    page: Int,
    pageSize: Int
): AndroidAutoSearchPage {
    val tokens = tokenizeSearchQuery(query)
    if (tokens.isEmpty() || page < 0 || pageSize <= 0) {
        return AndroidAutoSearchPage(entries = emptyList(), totalCount = 0)
    }

    val playlistEntries = snapshot.playlists
        .filter { playlist ->
            matchesAllTokens(
                searchablePlaylistText(
                    playlist = playlist,
                    tracks = snapshot.playlistTracks[playlist.id].orEmpty()
                ),
                tokens = tokens
            )
        }
        .map(AndroidAutoSearchEntry::PlaylistEntry)

    val trackEntries = buildSearchableTracks(snapshot)
        .filter { track -> matchesAllTokens(searchableTrackText(track), tokens) }
        .map(AndroidAutoSearchEntry::TrackEntry)

    val allEntries = playlistEntries + trackEntries
    val fromIndex = (page * pageSize).coerceAtMost(allEntries.size)
    val toIndex = (fromIndex + pageSize).coerceAtMost(allEntries.size)
    return AndroidAutoSearchPage(
        entries = allEntries.subList(fromIndex, toIndex),
        totalCount = allEntries.size
    )
}

private fun buildSearchableTracks(snapshot: AndroidAutoSearchSnapshot): List<Track> {
    val deduped = LinkedHashMap<String, Track>()
    snapshot.favorites.forEach { track -> deduped.putIfAbsent(track.identityKey(), track) }
    snapshot.recents.forEach { track -> deduped.putIfAbsent(track.identityKey(), track) }
    snapshot.playlistTracks.values.forEach { tracks ->
        tracks.forEach { track -> deduped.putIfAbsent(track.identityKey(), track) }
    }
    snapshot.localTracks.forEach { track -> deduped.putIfAbsent(track.identityKey(), track) }
    return deduped.values.toList()
}

private fun searchablePlaylistText(playlist: Playlist, tracks: List<Track>): String =
    buildString {
        append(playlist.name)
        tracks.forEach { track ->
            append(' ')
            append(track.title)
            append(' ')
            append(track.artist)
            if (track.album.isNotBlank()) {
                append(' ')
                append(track.album)
            }
        }
    }.normalizedSearchText()

private fun searchableTrackText(track: Track): String =
    buildString {
        append(track.title)
        append(' ')
        append(track.artist)
        if (track.album.isNotBlank()) {
            append(' ')
            append(track.album)
        }
    }.normalizedSearchText()

private fun tokenizeSearchQuery(query: String): List<String> =
    query
        .trim()
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(String::normalizedSearchText)

private fun matchesAllTokens(searchableText: String, tokens: List<String>): Boolean =
    tokens.all(searchableText::contains)

private fun String.normalizedSearchText(): String =
    lowercase(Locale.ROOT)

private fun Track.identityKey(): String = "${platform.id}:$id"
