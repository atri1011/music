package com.music.myapplication.domain.repository

import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LocalLibraryRepository {
    fun getFavorites(): Flow<List<Track>>
    suspend fun isFavorite(songId: String, platform: String): Boolean
    suspend fun applyFavoriteState(tracks: List<Track>): List<Track>
    suspend fun toggleFavorite(track: Track)
    fun getRecentPlays(limit: Int = 50): Flow<List<Track>>
    suspend fun recordRecentPlay(track: Track, positionMs: Long = 0L)
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Playlist
    suspend fun deletePlaylist(playlistId: String)
    suspend fun renamePlaylist(playlistId: String, newName: String)
    fun getPlaylistSongs(playlistId: String): Flow<List<Track>>
    suspend fun addToPlaylist(playlistId: String, track: Track)
    suspend fun addAllToPlaylist(playlistId: String, tracks: List<Track>)
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
