package com.music.myapplication.domain.repository

import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.PlaylistFolder
import com.music.myapplication.domain.model.SmartPlaylist
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.flow.Flow

data class RecentPlay(
    val track: Track,
    val playedAt: Long,
    val positionMs: Long,
    val playCount: Int
)

data class PlaybackEvent(
    val track: Track,
    val playedAt: Long,
    val listenDurationMs: Long,
    val playCount: Int
)

interface LocalLibraryRepository {
    fun getFavorites(): Flow<List<Track>>
    suspend fun isFavorite(songId: String, platform: String): Boolean
    suspend fun applyFavoriteState(tracks: List<Track>): List<Track>
    suspend fun toggleFavorite(track: Track)
    fun getRecentPlays(limit: Int = 50): Flow<List<Track>>
    fun getRecentPlayEntries(limit: Int = 100): Flow<List<RecentPlay>>
    fun getPlaybackEvents(limit: Int = 500): Flow<List<PlaybackEvent>>
    suspend fun recordRecentPlay(track: Track, positionMs: Long = 0L)
    fun getPlaylistFolders(): Flow<List<PlaylistFolder>>
    suspend fun createPlaylistFolder(name: String): PlaylistFolder
    suspend fun deletePlaylistFolder(folderId: String)
    suspend fun renamePlaylistFolder(folderId: String, newName: String)
    suspend fun movePlaylistToFolder(playlistId: String, folderId: String?)
    fun getSmartPlaylists(): Flow<List<SmartPlaylist>>
    fun getSmartPlaylistTracks(ruleId: String): Flow<List<Track>>
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(playlistId: String): Playlist?
    suspend fun createPlaylist(name: String): Playlist
    suspend fun deletePlaylist(playlistId: String)
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun updatePlaylistCover(playlistId: String, sourceUri: String?)
    fun getPlaylistSongs(playlistId: String): Flow<List<Track>>
    suspend fun addToPlaylist(playlistId: String, track: Track)
    suspend fun addAllToPlaylist(playlistId: String, tracks: List<Track>)
    suspend fun replacePlaylistSongs(playlistId: String, tracks: List<Track>)
    suspend fun removeFromPlaylist(playlistId: String, songId: String, platform: String)
    suspend fun getCachedLyrics(platform: String, songId: String): String?
    suspend fun cacheLyrics(platform: String, songId: String, lyrics: String)
    suspend fun getCachedTranslation(platform: String, songId: String): String?
    suspend fun cacheTranslation(platform: String, songId: String, translation: String)
    suspend fun getTrackPlayCount(songId: String, platform: String): Int
    suspend fun getFirstPlayDate(songId: String, platform: String): Long?
    suspend fun getRandomRecentTrack(): Track?
    fun getTopPlayedTracks(limit: Int = 10): Flow<List<Pair<Track, Int>>>
    fun getTotalPlayCount(): Flow<Int>
    fun getTotalListenDurationMs(): Flow<Long>
    fun getLocalTracks(): Flow<List<Track>>
    fun getLocalTrackCount(): Flow<Int>
    suspend fun syncLocalTracks(): Int
}
