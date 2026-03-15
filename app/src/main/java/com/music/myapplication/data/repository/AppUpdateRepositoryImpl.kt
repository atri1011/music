package com.music.myapplication.data.repository

import com.music.myapplication.BuildConfig
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.domain.model.AppUpdateInfo
import com.music.myapplication.domain.repository.AppUpdateRepository
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
            val downloadUrl = manifest.downloadUrl.trim()
            val latestVersionName = manifest.latestVersionName.trim()

            when {
                latestVersionCode <= 0 -> Result.Error(AppError.Parse(message = "更新清单 versionCode 无效"))
                downloadUrl.isBlank() -> Result.Error(AppError.Parse(message = "更新清单 downloadUrl 为空"))
                latestVersionName.isBlank() -> Result.Error(AppError.Parse(message = "更新清单 versionName 为空"))
                else -> Result.Success(
                    AppUpdateInfo(
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        downloadUrl = downloadUrl,
                        changelog = manifest.changelog?.trim()?.takeIf { it.isNotBlank() }
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
}
