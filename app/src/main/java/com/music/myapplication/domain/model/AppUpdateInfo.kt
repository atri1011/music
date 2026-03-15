package com.music.myapplication.domain.model

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val changelog: String? = null
)

