package com.music.myapplication.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateManifestDto(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val changelog: String? = null
)

