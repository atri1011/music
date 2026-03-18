package com.music.myapplication.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AppUpdateInstallResult {
    data object LaunchedInstaller : AppUpdateInstallResult
    data object RequiresUnknownSourcePermission : AppUpdateInstallResult
    data class Failed(val message: String) : AppUpdateInstallResult
}

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun verifySha256(filePath: String, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return@withContext false
        val expected = expectedSha256.trim().lowercase(Locale.ROOT)
        if (expected.length != 64) return@withContext false
        val actual = computeSha256(file)
        return@withContext actual.equals(expected, ignoreCase = true)
    }

    fun launchInstall(filePath: String): AppUpdateInstallResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return AppUpdateInstallResult.Failed("安装包不存在，请重新下载")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (startActivitySafely(permissionIntent)) {
                AppUpdateInstallResult.RequiresUnknownSourcePermission
            } else {
                AppUpdateInstallResult.Failed("无法打开未知来源安装授权页面")
            }
        }

        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse {
            return AppUpdateInstallResult.Failed("安装包 URI 生成失败")
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (startActivitySafely(installIntent)) {
            AppUpdateInstallResult.LaunchedInstaller
        } else {
            AppUpdateInstallResult.Failed("无法拉起系统安装器")
        }
    }

    private fun startActivitySafely(intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
        }.isSuccess
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
