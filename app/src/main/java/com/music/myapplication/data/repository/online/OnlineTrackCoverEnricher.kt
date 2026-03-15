package com.music.myapplication.data.repository.online

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.buildQqToplistDetailRequestBody
import com.music.myapplication.data.repository.extractKuwoSongCoverMap
import com.music.myapplication.data.repository.extractNeteaseSongCoverMap
import com.music.myapplication.data.repository.extractQqSongCoverMap
import com.music.myapplication.data.repository.extractQqToplistSongCoverMap
import com.music.myapplication.data.repository.isDigitsOnly
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

internal class OnlineTrackCoverEnricher(
    private val api: TuneHubApi,
    private val json: Json,
    private val neteaseDetailBatchSize: Int,
    private val qqDetailBatchSize: Int,
    private val kuwoDetailBatchSize: Int
) {
    private val neteaseCoverCache = ConcurrentHashMap<String, String>()
    private val qqCoverCache = ConcurrentHashMap<String, String>()
    private val kuwoCoverCache = ConcurrentHashMap<String, String>()

    suspend fun enrich(
        platform: Platform,
        result: Result<List<Track>>
    ): Result<List<Track>> {
        return when (result) {
            is Result.Success -> Result.Success(enrich(platform, result.data))
            is Result.Error -> result
            is Result.Loading -> result
        }
    }

    suspend fun enrich(platform: Platform, tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks
        return when (platform) {
            Platform.NETEASE -> enrichNeteaseCoverIfNeeded(tracks)
            Platform.QQ -> enrichQqCoverIfNeeded(tracks)
            Platform.KUWO -> enrichKuwoCoverIfNeeded(tracks)
            Platform.LOCAL -> tracks
        }
    }

    suspend fun enrichToplistTracks(
        platform: Platform,
        id: String,
        tracks: List<Track>
    ): List<Track> {
        val enrichedTracks = enrich(platform, tracks)
        if (platform != Platform.QQ || enrichedTracks.none { it.coverUrl.isBlank() }) {
            return enrichedTracks
        }

        val coverMap = runCatching {
            fetchQqToplistCoverMap(id)
        }.getOrDefault(emptyMap())

        if (coverMap.isEmpty()) return enrichedTracks
        return fillMissingTrackCovers(enrichedTracks, coverMap)
    }

    private suspend fun enrichNeteaseCoverIfNeeded(tracks: List<Track>): List<Track> {
        val missingCoverIds = tracks
            .asSequence()
            .filter { it.coverUrl.isBlank() && it.id.isDigitsOnly() }
            .map { it.id }
            .distinct()
            .toList()
        if (missingCoverIds.isEmpty()) return tracks

        val idsToFetch = missingCoverIds.filter { neteaseCoverCache[it].isNullOrBlank() }
        if (idsToFetch.isNotEmpty()) {
            runCatching { fetchNeteaseSongCovers(idsToFetch) }
                .getOrDefault(emptyMap())
                .forEach { (songId, coverUrl) ->
                    if (coverUrl.isNotBlank()) {
                        neteaseCoverCache[songId] = coverUrl
                    }
                }
        }

        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = neteaseCoverCache[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun enrichQqCoverIfNeeded(tracks: List<Track>): List<Track> {
        val missingCoverIds = tracks
            .asSequence()
            .filter { it.coverUrl.isBlank() && it.id.isNotBlank() }
            .map { it.id }
            .distinct()
            .toList()
        if (missingCoverIds.isEmpty()) return tracks

        val idsToFetch = missingCoverIds.filter { qqCoverCache[it].isNullOrBlank() }
        if (idsToFetch.isNotEmpty()) {
            runCatching { fetchQqSongCovers(idsToFetch) }
                .getOrDefault(emptyMap())
                .forEach { (songId, coverUrl) ->
                    if (coverUrl.isNotBlank()) {
                        qqCoverCache[songId] = coverUrl
                    }
                }
        }

        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = qqCoverCache[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun enrichKuwoCoverIfNeeded(tracks: List<Track>): List<Track> {
        val missingCoverIds = tracks
            .asSequence()
            .filter { it.coverUrl.isBlank() && it.id.isDigitsOnly() }
            .map { it.id }
            .distinct()
            .toList()
        if (missingCoverIds.isEmpty()) return tracks

        val idsToFetch = missingCoverIds.filter { kuwoCoverCache[it].isNullOrBlank() }
        if (idsToFetch.isNotEmpty()) {
            runCatching { fetchKuwoSongCovers(idsToFetch) }
                .getOrDefault(emptyMap())
                .forEach { (songId, coverUrl) ->
                    if (coverUrl.isNotBlank()) {
                        kuwoCoverCache[songId] = coverUrl
                    }
                }
        }

        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = kuwoCoverCache[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun fetchNeteaseSongCovers(songIds: List<String>): Map<String, String> {
        if (songIds.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        songIds.chunked(neteaseDetailBatchSize).forEach { chunk ->
            val idsParam = chunk.joinToString(prefix = "[", postfix = "]")
            val response = api.getNeteaseSongDetail(ids = idsParam)
            result.putAll(extractNeteaseSongCoverMap(response))
        }
        return result
    }

    private suspend fun fetchQqSongCovers(songMids: List<String>): Map<String, String> {
        if (songMids.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        songMids.chunked(qqDetailBatchSize).forEach { chunk ->
            val midsParam = chunk.joinToString(",")
            val response = api.getQqSongDetail(songMid = midsParam)
            result.putAll(extractQqSongCoverMap(response))
        }
        return result
    }

    private suspend fun fetchQqToplistCoverMap(topId: String): Map<String, String> {
        val topIdInt = topId.toIntOrNull() ?: return emptyMap()
        val response = api.postQqMusicu(body = buildQqToplistDetailRequestBody(topIdInt))
        return extractQqToplistSongCoverMap(response)
    }

    private fun fillMissingTrackCovers(
        tracks: List<Track>,
        coverMap: Map<String, String>
    ): List<Track> {
        if (tracks.isEmpty() || coverMap.isEmpty()) return tracks
        return tracks.map { track ->
            if (track.coverUrl.isNotBlank()) return@map track
            val coverUrl = coverMap[track.id].orEmpty()
            if (coverUrl.isBlank()) track else track.copy(coverUrl = coverUrl)
        }
    }

    private suspend fun fetchKuwoSongCovers(songIds: List<String>): Map<String, String> {
        if (songIds.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        songIds.chunked(kuwoDetailBatchSize).forEach { chunk ->
            val ridParam = chunk.joinToString(",") { "MUSIC_$it" }
            val responseBody = api.getKuwoSongMetaRaw(rid = ridParam)
            val raw = responseBody.use { it.string() }
            result.putAll(extractKuwoSongCoverMap(raw, json))
        }
        return result
    }
}
