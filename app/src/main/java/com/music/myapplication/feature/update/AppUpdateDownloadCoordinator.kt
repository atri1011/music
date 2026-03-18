package com.music.myapplication.feature.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.music.myapplication.domain.model.AppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AppUpdateDownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(update: AppUpdateInfo) {
        val inputData = Data.Builder()
            .putString(AppUpdateDownloadWorker.KEY_INPUT_APK_URL, update.apkUrl)
            .putInt(AppUpdateDownloadWorker.KEY_INPUT_VERSION_CODE, update.latestVersionCode)
            .putLong(AppUpdateDownloadWorker.KEY_INPUT_EXPECTED_FILE_SIZE, update.fileSizeBytes)
            .build()

        val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_APP_UPDATE_DOWNLOAD)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun observeDownloadState(): Flow<AppUpdateDownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { infos ->
                infos.lastOrNull()?.toAppUpdateDownloadState() ?: AppUpdateDownloadState()
            }
    }

    private fun WorkInfo.toAppUpdateDownloadState(): AppUpdateDownloadState {
        val downloadedBytes = progress.getLong(AppUpdateDownloadWorker.KEY_PROGRESS_DOWNLOADED_BYTES, 0L)
        val totalBytes = progress.getLong(AppUpdateDownloadWorker.KEY_PROGRESS_TOTAL_BYTES, 0L)
        val rawProgress = progress.getInt(AppUpdateDownloadWorker.KEY_PROGRESS_PERCENT, -1)
        val progressPercent = rawProgress.takeIf { it >= 0 }
        return when (state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.ENQUEUED,
                progressPercent = progressPercent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
            WorkInfo.State.RUNNING -> AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.RUNNING,
                progressPercent = progressPercent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
            WorkInfo.State.SUCCEEDED -> AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.SUCCEEDED,
                progressPercent = 100,
                downloadedBytes = outputData.getLong(AppUpdateDownloadWorker.KEY_OUTPUT_FILE_SIZE, downloadedBytes),
                totalBytes = outputData.getLong(AppUpdateDownloadWorker.KEY_OUTPUT_FILE_SIZE, totalBytes),
                localFilePath = outputData.getString(AppUpdateDownloadWorker.KEY_OUTPUT_FILE_PATH)
            )
            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED -> AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.FAILED,
                progressPercent = progressPercent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                errorMessage = outputData.getString(AppUpdateDownloadWorker.KEY_OUTPUT_ERROR_MESSAGE)
            )
        }
    }

    companion object {
        const val TAG_APP_UPDATE_DOWNLOAD = "app_update_download"
        private const val UNIQUE_WORK_NAME = "app_update_download_work"
    }
}
