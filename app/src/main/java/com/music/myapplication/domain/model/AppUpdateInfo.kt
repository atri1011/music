package com.music.myapplication.domain.model

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val sha256: String,
    val fileSizeBytes: Long,
    val changelog: String? = null,
    val isForceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val publishedAt: String? = null
) {
    // 兼容旧调用方命名，过渡期仍可使用。
    val downloadUrl: String
        get() = apkUrl

    fun isSkipAllowed(currentVersionCode: Int): Boolean {
        return !isForceUpdate && currentVersionCode >= minSupportedVersionCode
    }
}
