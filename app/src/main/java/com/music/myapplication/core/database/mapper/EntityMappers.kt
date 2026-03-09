package com.music.myapplication.core.database.mapper

import com.music.myapplication.core.database.entity.*
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track

fun FavoriteEntity.toTrack(): Track = Track(
    id = songId,
    platform = Platform.fromId(platform),
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs,
    isFavorite = true
)

fun Track.toFavoriteEntity(): FavoriteEntity = FavoriteEntity(
    songId = id,
    platform = platform.id,
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs
)

fun RecentPlayEntity.toTrack(): Track = Track(
    id = songId,
    platform = Platform.fromId(platform),
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    durationMs = durationMs
)

fun Track.toRecentPlayEntity(positionMs: Long = 0L): RecentPlayEntity = RecentPlayEntity(
    songId = id,
    platform = platform.id,
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    durationMs = durationMs,
    positionMs = positionMs
)

fun PlaylistSongEntity.toTrack(): Track = Track(
    id = songId,
    platform = Platform.fromId(platform),
    title = title,
    artist = artist,
    coverUrl = coverUrl,
    durationMs = durationMs
)

fun Track.toPlaylistSongEntity(playlistId: String, order: Int): PlaylistSongEntity =
    PlaylistSongEntity(
        playlistId = playlistId,
        songId = id,
        platform = platform.id,
        orderInPlaylist = order,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        durationMs = durationMs
    )

fun DownloadedTrackEntity.toTrack(): Track = Track(
    id = songId,
    platform = Platform.fromId(platform),
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs,
    playableUrl = filePath,
    quality = quality
)

fun Track.toDownloadedTrackEntity(
    filePath: String = "",
    fileSizeBytes: Long = 0L,
    status: String = DownloadedTrackEntity.DownloadStatus.DOWNLOADING
): DownloadedTrackEntity = DownloadedTrackEntity(
    songId = id,
    platform = platform.id,
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    quality = quality,
    downloadStatus = status
)

fun LocalTrackEntity.toTrack(): Track = Track(
    id = mediaStoreId.toString(),
    platform = Platform.LOCAL,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    playableUrl = filePath
)
