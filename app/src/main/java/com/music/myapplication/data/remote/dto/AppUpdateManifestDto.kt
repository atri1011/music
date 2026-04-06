package com.music.myapplication.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateManifestDto(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val apkUrl: String? = null,
    val downloadUrl: String? = null,
    val sha256: String = "",
    val fileSizeBytes: Long = 0L,
    val changelog: String? = null,
    val fullChangelogUrl: String? = null,
    val isForceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val publishedAt: String? = null
)
