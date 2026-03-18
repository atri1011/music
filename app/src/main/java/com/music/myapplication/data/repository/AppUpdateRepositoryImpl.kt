package com.music.myapplication.data.repository

import com.music.myapplication.BuildConfig
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.AppUpdateInfo
import com.music.myapplication.domain.repository.AppUpdateRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val api: TuneHubApi
) : AppUpdateRepository {

    override suspend fun fetchLatest(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        val repo = BuildConfig.APP_UPDATE_REPO.trim()
        if (repo.isBlank()) return@withContext Result.Success(null)

        return@withContext try {
            val feedUrl = resolveLatestUrl(repo)
            val manifest = api.fetchAppUpdateManifest(feedUrl)
            val latestVersionCode = manifest.latestVersionCode
            val latestVersionName = manifest.latestVersionName.trim()
            val apkUrl = resolveApkUrl(manifest.apkUrl, manifest.downloadUrl)
            val sha256 = manifest.sha256.trim().lowercase(Locale.ROOT)
            val fileSizeBytes = manifest.fileSizeBytes
            val minSupportedVersionCode = manifest.minSupportedVersionCode

            when {
                latestVersionCode <= 0 -> Result.Error(AppError.Parse(message = "更新清单 versionCode 无效"))
                latestVersionName.isBlank() -> Result.Error(AppError.Parse(message = "更新清单 versionName 为空"))
                apkUrl.isBlank() -> Result.Error(AppError.Parse(message = "更新清单 apkUrl 为空"))
                !isDirectDownloadUrl(apkUrl) -> Result.Error(AppError.Parse(message = "更新清单 apkUrl 非直连下载地址"))
                !SHA256_REGEX.matches(sha256) -> Result.Error(AppError.Parse(message = "更新清单 sha256 非法"))
                fileSizeBytes <= 0L -> Result.Error(AppError.Parse(message = "更新清单 fileSizeBytes 无效"))
                minSupportedVersionCode < 0 -> Result.Error(AppError.Parse(message = "更新清单 minSupportedVersionCode 无效"))
                else -> Result.Success(
                    AppUpdateInfo(
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        apkUrl = apkUrl,
                        sha256 = sha256,
                        fileSizeBytes = fileSizeBytes,
                        changelog = manifest.changelog?.trim()?.takeIf { it.isNotBlank() },
                        isForceUpdate = manifest.isForceUpdate,
                        minSupportedVersionCode = minSupportedVersionCode,
                        publishedAt = manifest.publishedAt?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Result.Error(AppError.Network(message = "更新检查失败", cause = e))
        }
    }

    private suspend fun resolveLatestUrl(repo: String): String {
        return try {
            val resolveUrl = "https://data.jsdelivr.com/v1/packages/gh/$repo/resolved?specifier=latest"
            val json = api.fetchJsonElement(resolveUrl)
            val version = json.jsonObject["version"]?.jsonPrimitive?.contentOrNull
            if (!version.isNullOrBlank()) {
                "https://cdn.jsdelivr.net/gh/$repo@$version/update.json"
            } else {
                fallbackUrl(repo)
            }
        } catch (_: Exception) {
            fallbackUrl(repo)
        }
    }

    private fun fallbackUrl(repo: String): String =
        "https://cdn.jsdelivr.net/gh/$repo@main/update.json"

    private fun resolveApkUrl(apkUrl: String?, downloadUrl: String?): String {
        return apkUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: downloadUrl?.trim().orEmpty()
    }

    private fun isDirectDownloadUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)
    }

    companion object {
        private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
    }
}
