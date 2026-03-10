package com.music.myapplication.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.music.myapplication.core.database.dao.*
import com.music.myapplication.core.database.entity.LyricsCacheEntity
import com.music.myapplication.core.database.entity.PlaylistEntity
import com.music.myapplication.core.database.mapper.*
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.core.local.LocalMusicScanner
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.LocalLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesDao: FavoritesDao,
    private val recentPlaysDao: RecentPlaysDao,
    private val playlistsDao: PlaylistsDao,
    private val playlistSongsDao: PlaylistSongsDao,
    private val lyricsCacheDao: LyricsCacheDao,
    private val localTracksDao: LocalTracksDao,
    private val localMusicScanner: LocalMusicScanner
) : LocalLibraryRepository {

    override fun getFavorites(): Flow<List<Track>> =
        favoritesDao.getAll().map { list ->
            rehydrateLocalPlayableUrls(list.map { it.toTrack() })
        }

    override suspend fun isFavorite(songId: String, platform: String): Boolean =
        favoritesDao.isFavorite(songId, platform)

    override suspend fun applyFavoriteState(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks

        val trackKeys = tracks.map(::favoriteKeyOf).distinct()
        val favoriteKeys = favoritesDao.getFavoriteKeys(trackKeys).toHashSet()
        if (favoriteKeys.isEmpty()) {
            return tracks.map { it.copy(isFavorite = false) }
        }

        return tracks.map { track ->
            track.copy(isFavorite = favoriteKeyOf(track) in favoriteKeys)
        }
    }

    override suspend fun toggleFavorite(track: Track) {
        if (favoritesDao.isFavorite(track.id, track.platform.id)) {
            favoritesDao.delete(track.id, track.platform.id)
        } else {
            favoritesDao.insert(track.toFavoriteEntity())
        }
    }

    override fun getRecentPlays(limit: Int): Flow<List<Track>> =
        recentPlaysDao.getRecent(limit).map { list ->
            rehydrateLocalPlayableUrls(list.map { it.toTrack() })
        }

    override suspend fun recordRecentPlay(track: Track, positionMs: Long) {
        recentPlaysDao.insertOrUpdate(
            songId = track.id,
            platform = track.platform.id,
            title = track.title,
            artist = track.artist,
            coverUrl = track.coverUrl,
            durationMs = track.durationMs,
            playedAt = System.currentTimeMillis(),
            positionMs = positionMs
        )
        recentPlaysDao.trimOldEntries()
    }

    override fun getPlaylists(): Flow<List<Playlist>> =
        playlistsDao.getAllWithStats().map { list ->
            list.map { entity ->
                Playlist(
                    id = entity.playlistId,
                    name = entity.name,
                    coverUrl = entity.coverUrl,
                    trackCount = entity.trackCount,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }

    override suspend fun getPlaylistById(playlistId: String): Playlist? =
        playlistsDao.getById(playlistId)?.toPlaylist()

    override suspend fun createPlaylist(name: String): Playlist {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        playlistsDao.insert(PlaylistEntity(playlistId = id, name = name, createdAt = now, updatedAt = now))
        return Playlist(id = id, name = name, createdAt = now, updatedAt = now)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        val existing = playlistsDao.getById(playlistId)
        playlistsDao.delete(playlistId)
        deleteManagedPlaylistCover(existing?.coverUrl.orEmpty())
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        val entity = playlistsDao.getById(playlistId) ?: return
        playlistsDao.update(entity.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun updatePlaylistCover(playlistId: String, sourceUri: String?) {
        val entity = playlistsDao.getById(playlistId) ?: return
        val newCoverUrl = sourceUri
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { copyPlaylistCoverToPrivateDir(playlistId, it.toUri()) }
            .orEmpty()
        val previousCoverUrl = entity.coverUrl
        playlistsDao.update(
            entity.copy(
                coverUrl = newCoverUrl,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (previousCoverUrl != newCoverUrl) {
            deleteManagedPlaylistCover(previousCoverUrl)
        }
    }

    override fun getPlaylistSongs(playlistId: String): Flow<List<Track>> =
        playlistSongsDao.getSongsByPlaylist(playlistId).map { list ->
            rehydrateLocalPlayableUrls(list.map { it.toTrack() })
        }

    override suspend fun addToPlaylist(playlistId: String, track: Track) {
        addAllToPlaylist(playlistId, listOf(track))
    }

    override suspend fun addAllToPlaylist(playlistId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val startOrder = playlistSongsDao.getMaxOrder(playlistId)?.plus(1) ?: 0
        playlistSongsDao.insertAll(
            tracks.mapIndexed { index, track ->
                track.toPlaylistSongEntity(playlistId, startOrder + index)
            }
        )
        touchPlaylist(playlistId)
    }

    override suspend fun replacePlaylistSongs(playlistId: String, tracks: List<Track>) {
        playlistSongsDao.replacePlaylistSongs(
            playlistId = playlistId,
            entities = tracks.mapIndexed { index, track ->
                track.toPlaylistSongEntity(playlistId, index)
            }
        )
        touchPlaylist(playlistId)
    }

    override suspend fun removeFromPlaylist(playlistId: String, songId: String, platform: String) {
        playlistSongsDao.delete(playlistId, songId, platform)
        touchPlaylist(playlistId)
    }

    override suspend fun getCachedLyrics(platform: String, songId: String): String? {
        val key = "$platform:$songId"
        return lyricsCacheDao.get(key)?.lyricText
    }

    override suspend fun cacheLyrics(platform: String, songId: String, lyrics: String) {
        val key = "$platform:$songId"
        lyricsCacheDao.insert(LyricsCacheEntity(cacheKey = key, lyricText = lyrics))
    }

    override suspend fun getCachedTranslation(platform: String, songId: String): String? {
        val key = "$platform:$songId:trans"
        return lyricsCacheDao.get(key)?.lyricText
    }

    override suspend fun cacheTranslation(platform: String, songId: String, translation: String) {
        val key = "$platform:$songId:trans"
        lyricsCacheDao.insert(LyricsCacheEntity(cacheKey = key, lyricText = translation))
    }

    override suspend fun getTrackPlayCount(songId: String, platform: String): Int =
        recentPlaysDao.getPlayCount(songId, platform) ?: 0

    override suspend fun getFirstPlayDate(songId: String, platform: String): Long? =
        recentPlaysDao.getFirstPlayDate(songId, platform)

    override suspend fun getRandomRecentTrack(): Track? =
        recentPlaysDao.getRandomTrack()?.toTrack()?.let { rehydrateLocalPlayableUrl(it) }

    override fun getTopPlayedTracks(limit: Int): Flow<List<Pair<Track, Int>>> =
        recentPlaysDao.getTopPlayed(limit).map { list ->
            val tracks = rehydrateLocalPlayableUrls(list.map { entity -> entity.toTrack() })
            tracks.mapIndexed { index, track -> track to list[index].playCount }
        }

    override fun getTotalPlayCount(): Flow<Int> =
        recentPlaysDao.getTotalPlayCount()

    override fun getTotalListenDurationMs(): Flow<Long> =
        recentPlaysDao.getTotalListenDurationMs()

    override fun getLocalTracks(): Flow<List<Track>> =
        localTracksDao.getAll().map { list -> list.map { it.toTrack() } }

    override fun getLocalTrackCount(): Flow<Int> =
        localTracksDao.count()

    override suspend fun syncLocalTracks(): Int =
        localMusicScanner.sync()

    private fun favoriteKeyOf(track: Track): String = "${track.platform.id}:${track.id}"

    private suspend fun rehydrateLocalPlayableUrls(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks

        val localTrackIds = tracks.asSequence()
            .filter { it.platform == Platform.LOCAL && it.playableUrl.isBlank() }
            .mapNotNull { it.id.toLongOrNull() }
            .distinct()
            .toList()
        if (localTrackIds.isEmpty()) return tracks

        val localTracksById = localTracksDao.getByIds(localTrackIds)
            .associateBy { it.mediaStoreId.toString() }

        if (localTracksById.isEmpty()) return tracks

        return tracks.map { track ->
            if (track.platform != Platform.LOCAL || track.playableUrl.isNotBlank()) {
                track
            } else {
                localTracksById[track.id]?.let { localTrack ->
                    track.copy(
                        album = track.album.ifBlank { localTrack.album },
                        durationMs = track.durationMs.takeIf { it > 0L } ?: localTrack.durationMs,
                        playableUrl = localTrack.filePath
                    )
                } ?: track
            }
        }
    }

    private suspend fun rehydrateLocalPlayableUrl(track: Track): Track =
        rehydrateLocalPlayableUrls(listOf(track)).first()

    private suspend fun touchPlaylist(playlistId: String) {
        val entity = playlistsDao.getById(playlistId) ?: return
        playlistsDao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun PlaylistEntity.toPlaylist(): Playlist = Playlist(
        id = playlistId,
        name = name,
        coverUrl = coverUrl,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun copyPlaylistCoverToPrivateDir(playlistId: String, sourceUri: Uri): String {
        val destinationDir = File(context.filesDir, PLAYLIST_COVER_DIRECTORY).apply { mkdirs() }
        val extension = resolvePlaylistCoverExtension(sourceUri)
        val destinationFile = File(
            destinationDir,
            "playlist_${playlistId}_${System.currentTimeMillis()}.$extension"
        )

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("无法读取选中的封面图片")

        return destinationFile.absolutePath
    }

    private fun resolvePlaylistCoverExtension(sourceUri: Uri): String {
        val mimeType = context.contentResolver.getType(sourceUri).orEmpty()
        val fromMime = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        if (fromMime != null) return fromMime

        val path = sourceUri.lastPathSegment.orEmpty()
        val extension = path.substringAfterLast('.', "").lowercase()
        return extension.takeIf { it.length in 2..5 } ?: DEFAULT_PLAYLIST_COVER_EXTENSION
    }

    private fun deleteManagedPlaylistCover(rawPath: String) {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank()) return

        val managedDirectoryPath = File(context.filesDir, PLAYLIST_COVER_DIRECTORY).absolutePath
        val targetFile = File(trimmed)
        if (!targetFile.exists()) return
        if (!targetFile.absolutePath.startsWith(managedDirectoryPath)) return
        targetFile.delete()
    }

    private companion object {
        const val PLAYLIST_COVER_DIRECTORY = "playlist_covers"
        const val DEFAULT_PLAYLIST_COVER_EXTENSION = "jpg"
    }
}
