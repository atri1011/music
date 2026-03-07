package com.music.myapplication.domain.repository

import com.music.myapplication.domain.model.Track

interface RecommendationRepository {
    suspend fun getDailyRecommendedTracks(limit: Int = 30): List<Track>
    suspend fun getFmTrack(): Track?
    suspend fun getRecommendedPlaylists(): List<ToplistInfo>
    suspend fun getSimilarTracks(track: Track, limit: Int = 5): List<Track>
}
