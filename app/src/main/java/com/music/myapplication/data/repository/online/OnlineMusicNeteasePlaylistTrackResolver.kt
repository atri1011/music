package com.music.myapplication.data.repository.online

import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.extractNeteasePlaylistTrackIds
import com.music.myapplication.data.repository.extractNeteasePlaylistTracks
import com.music.myapplication.data.repository.extractNeteaseSongTracks
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.json.JsonElement

internal class OnlineMusicNeteasePlaylistTrackResolver(
    private val api: TuneHubApi,
    private val neteaseDetailBatchSize: Int
) {
    suspend fun resolvePlaylistTracks(response: JsonElement): List<Track> {
        val playlistTracks = extractNeteasePlaylistTracks(response)
        val trackIds = extractNeteasePlaylistTrackIds(response)
        if (trackIds.isEmpty()) return playlistTracks

        val resolvedTracks = LinkedHashMap<String, Track>()
        playlistTracks.forEach { track ->
            if (track.id.isNotBlank()) {
                resolvedTracks.putIfAbsent(track.id, track)
            }
        }

        val missingTrackIds = trackIds.filterNot(resolvedTracks::containsKey)
        if (missingTrackIds.isNotEmpty()) {
            fetchSongs(missingTrackIds).forEach { track ->
                if (track.id.isNotBlank()) {
                    resolvedTracks.putIfAbsent(track.id, track)
                }
            }
        }

        return trackIds.mapNotNull(resolvedTracks::get)
            .ifEmpty { playlistTracks }
    }

    private suspend fun fetchSongs(songIds: List<String>): List<Track> {
        if (songIds.isEmpty()) return emptyList()

        val tracks = mutableListOf<Track>()
        songIds.chunked(neteaseDetailBatchSize).forEach { chunk ->
            val idsParam = chunk.joinToString(prefix = "[", postfix = "]")
            val response = api.getNeteaseSongDetail(ids = idsParam)
            tracks += extractNeteaseSongTracks(response)
        }
        return tracks
    }
}
