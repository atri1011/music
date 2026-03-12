package com.music.myapplication.domain.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.NeteaseAccountSession
import com.music.myapplication.domain.model.NeteaseQrLoginPayload
import com.music.myapplication.domain.model.NeteaseQrLoginStatus
import com.music.myapplication.domain.model.NeteaseSyncSummary
import kotlinx.coroutines.flow.Flow

interface NeteaseAccountRepository {
    val session: Flow<NeteaseAccountSession?>
    val isConfigured: Flow<Boolean>

    suspend fun refreshLoginStatus(): Result<NeteaseAccountSession?>
    suspend fun loginWithPassword(phone: String, password: String): Result<NeteaseAccountSession>
    suspend fun sendCaptcha(phone: String): Result<Unit>
    suspend fun loginWithCaptcha(phone: String, captcha: String): Result<NeteaseAccountSession>
    suspend fun createQrLogin(): Result<NeteaseQrLoginPayload>
    suspend fun checkQrLogin(key: String): Result<NeteaseQrLoginStatus>
    suspend fun syncLocalLibrary(): Result<NeteaseSyncSummary>
    suspend fun logout()
}
