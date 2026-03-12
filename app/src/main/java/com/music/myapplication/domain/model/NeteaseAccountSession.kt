package com.music.myapplication.domain.model

data class NeteaseAccountSession(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String = "",
    val cookie: String,
    val lastLoginAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = 0L
)

data class NeteaseQrLoginPayload(
    val key: String,
    val qrImageUrl: String = "",
    val qrUrl: String = ""
)

enum class NeteaseQrLoginState {
    WAITING,
    SCANNED,
    AUTHORIZED,
    EXPIRED
}

data class NeteaseQrLoginStatus(
    val state: NeteaseQrLoginState,
    val message: String,
    val session: NeteaseAccountSession? = null
)

data class NeteaseSyncSummary(
    val syncedPlaylistCount: Int,
    val syncedFavoriteCount: Int
)
