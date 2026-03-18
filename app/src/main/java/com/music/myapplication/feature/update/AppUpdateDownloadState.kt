package com.music.myapplication.feature.update

data class AppUpdateDownloadState(
    val status: AppUpdateDownloadStatus = AppUpdateDownloadStatus.IDLE,
    val progressPercent: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val localFilePath: String? = null,
    val errorMessage: String? = null
)

enum class AppUpdateDownloadStatus {
    IDLE,
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
