package com.music.myapplication.domain.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.AppUpdateInfo

interface AppUpdateRepository {
    suspend fun fetchLatest(): Result<AppUpdateInfo?>
}

