package com.music.myapplication.feature.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class AppUpdateDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString(KEY_INPUT_APK_URL)?.trim().orEmpty()
        val versionCode = inputData.getInt(KEY_INPUT_VERSION_CODE, 0)
        val expectedFileSize = inputData.getLong(KEY_INPUT_EXPECTED_FILE_SIZE, 0L).coerceAtLeast(0L)

        if (apkUrl.isBlank() || versionCode <= 0) {
            return@withContext Result.failure(
                errorData("更新任务参数无效")
            )
        }

        val updatesDirectory = File(context.getExternalFilesDir(null) ?: context.filesDir, UPDATE_DIRECTORY_NAME)
            .apply { mkdirs() }
        val targetFile = File(updatesDirectory, "app-update-v$versionCode.apk")
        val tempFile = File(updatesDirectory, "app-update-v$versionCode.apk.part")

        try {
            val existingBytes = if (tempFile.exists()) tempFile.length().coerceAtLeast(0L) else 0L
            val requestBuilder = Request.Builder().url(apkUrl)
            if (existingBytes > 0L) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                val message = "下载失败，HTTP ${response.code}"
                return@withContext if (shouldRetry()) {
                    Result.retry()
                } else {
                    Result.failure(errorData(message))
                }
            }

            val responseBody = response.body ?: return@withContext Result.failure(
                errorData("下载失败，响应体为空")
            )

            val appendMode = response.code == 206 && existingBytes > 0L
            val baseDownloaded = if (appendMode) existingBytes else 0L
            if (!appendMode && tempFile.exists()) {
                tempFile.delete()
            }

            val responseLength = responseBody.contentLength().coerceAtLeast(0L)
            val totalBytes = resolveTotalBytes(
                expectedFileSize = expectedFileSize,
                responseContentLength = responseLength,
                alreadyDownloadedBytes = baseDownloaded
            )

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(tempFile, appendMode).use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = baseDownloaded
                    var lastProgressBytes = baseDownloaded
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read < 0) break
                        if (isStopped) {
                            return@withContext Result.failure(errorData("下载任务已取消"))
                        }
                        outputStream.write(buffer, 0, read)
                        downloadedBytes += read.toLong()
                        if (downloadedBytes - lastProgressBytes >= PROGRESS_CHUNK_BYTES ||
                            (totalBytes > 0L && downloadedBytes >= totalBytes)
                        ) {
                            setProgress(buildProgressData(downloadedBytes, totalBytes))
                            lastProgressBytes = downloadedBytes
                        }
                    }
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val actualSize = targetFile.length().coerceAtLeast(0L)
            if (expectedFileSize > 0L && actualSize != expectedFileSize) {
                targetFile.delete()
                return@withContext Result.failure(
                    errorData("下载文件大小不匹配，期望 $expectedFileSize，实际 $actualSize")
                )
            }

            Result.success(
                Data.Builder()
                    .putString(KEY_OUTPUT_FILE_PATH, targetFile.absolutePath)
                    .putLong(KEY_OUTPUT_FILE_SIZE, actualSize)
                    .build()
            )
        } catch (e: IOException) {
            if (shouldRetry()) {
                Result.retry()
            } else {
                Result.failure(errorData(e.message ?: "下载失败"))
            }
        } catch (e: Exception) {
            Result.failure(errorData(e.message ?: "下载失败"))
        }
    }

    private fun resolveTotalBytes(
        expectedFileSize: Long,
        responseContentLength: Long,
        alreadyDownloadedBytes: Long
    ): Long {
        if (expectedFileSize > 0L) return expectedFileSize
        if (responseContentLength <= 0L) return 0L
        return if (alreadyDownloadedBytes > 0L) {
            alreadyDownloadedBytes + responseContentLength
        } else {
            responseContentLength
        }
    }

    private fun shouldRetry(): Boolean = runAttemptCount < MAX_RETRY_ATTEMPTS

    private fun errorData(message: String): Data {
        return Data.Builder()
            .putString(KEY_OUTPUT_ERROR_MESSAGE, message)
            .build()
    }

    private fun buildProgressData(downloadedBytes: Long, totalBytes: Long): Data {
        val progressPercent = if (totalBytes > 0L) {
            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            -1
        }
        return Data.Builder()
            .putLong(KEY_PROGRESS_DOWNLOADED_BYTES, downloadedBytes)
            .putLong(KEY_PROGRESS_TOTAL_BYTES, totalBytes)
            .putInt(KEY_PROGRESS_PERCENT, progressPercent)
            .build()
    }

    companion object {
        const val KEY_INPUT_APK_URL = "apk_url"
        const val KEY_INPUT_VERSION_CODE = "version_code"
        const val KEY_INPUT_EXPECTED_FILE_SIZE = "expected_file_size"

        const val KEY_PROGRESS_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_PROGRESS_TOTAL_BYTES = "total_bytes"
        const val KEY_PROGRESS_PERCENT = "progress_percent"

        const val KEY_OUTPUT_FILE_PATH = "output_file_path"
        const val KEY_OUTPUT_FILE_SIZE = "output_file_size"
        const val KEY_OUTPUT_ERROR_MESSAGE = "output_error_message"

        const val UPDATE_DIRECTORY_NAME = "updates"

        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PROGRESS_CHUNK_BYTES = 256L * 1024L
    }
}
