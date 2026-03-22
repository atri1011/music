package com.music.myapplication.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.database.AppDatabase
import com.music.myapplication.core.database.dao.DownloadedTracksDao
import com.music.myapplication.core.database.dao.LocalTracksDao
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import com.music.myapplication.core.database.entity.LocalTrackEntity
import com.music.myapplication.core.network.NetworkMonitor
import com.music.myapplication.data.repository.shareRefererFor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CancellationException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val appDatabase: AppDatabase,
    private val downloadedTracksDao: DownloadedTracksDao,
    private val localTracksDao: LocalTracksDao,
    private val okHttpClient: OkHttpClient,
    private val playerPreferences: PlayerPreferences,
    private val networkMonitor: NetworkMonitor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val platform = inputData.getString(KEY_PLATFORM) ?: return@withContext Result.failure()
        val requestId = id.toString()
        val title = inputData.getString(KEY_TITLE) ?: ""
        val artist = inputData.getString(KEY_ARTIST) ?: ""
        val album = inputData.getString(KEY_ALBUM) ?: ""
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val playableUrl = inputData.getString(KEY_PLAYABLE_URL) ?: return@withContext Result.failure()

        if (!isCurrentRequest(songId, platform, requestId)) {
            return@withContext Result.success()
        }
        if (playerPreferences.wifiOnly.first() && !networkMonitor.isUnmeteredConnection()) {
            return@withContext Result.retry()
        }
        if (!hasLegacyPublicWritePermission(context)) {
            val marked = downloadedTracksDao.markFailed(
                songId = songId,
                platform = platform,
                requestId = requestId,
                failureReason = "缺少公共存储写入权限，无法下载到系统音乐目录"
            )
            return@withContext if (marked > 0) Result.failure() else Result.success()
        }

        var storedArtifact: StoredDownloadArtifact? = null

        try {
            setForeground(createForegroundInfo(title, artist))

            val requestBuilder = Request.Builder().url(playableUrl).get()
            shareRefererFor(playableUrl)?.let { referer ->
                requestBuilder.header("Referer", referer)
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext handleFailure(
                        songId = songId,
                        platform = platform,
                        requestId = requestId,
                        progressPercent = 0,
                        failureReason = "下载失败，HTTP ${response.code}",
                        action = classifyDownloadFailure(httpCode = response.code)
                    )
                }

                val responseBody = response.body ?: return@withContext handleFailure(
                    songId = songId,
                    platform = platform,
                    requestId = requestId,
                    progressPercent = 0,
                    failureReason = "下载失败，响应体为空",
                    action = DownloadFailureAction.FAIL
                )

                responseBody.use { body ->
                    storedArtifact = body.byteStream().use { inputStream ->
                        writeTrackToPublicStorage(
                            context = context,
                            songId = songId,
                            title = title,
                            artist = artist,
                            album = album,
                            requestUrl = response.request.url.toString(),
                            contentTypeHeader = response.header("Content-Type"),
                            inputStream = inputStream,
                            expectedLength = body.contentLength(),
                            onProgress = { progress ->
                                val updated = downloadedTracksDao.updateProgress(songId, platform, requestId, progress)
                                if (updated == 0) {
                                    throw StaleDownloadRequestException()
                                }
                            },
                            shouldAbort = { isStopped }
                        )
                    }

                    persistCompletedDownload(
                        appDatabase = appDatabase,
                        downloadedTracksDao = downloadedTracksDao,
                        localTracksDao = localTracksDao,
                        songId = songId,
                        platform = platform,
                        requestId = requestId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        storedArtifact = storedArtifact!!
                    )
                    storedArtifact = null
                }
            }

            Result.success()
        } catch (e: CancellationException) {
            storedArtifact?.let {
                cleanupStoredArtifact(context, it)
                storedArtifact = null
            }
            val marked = downloadedTracksDao.markFailed(
                songId = songId,
                platform = platform,
                requestId = requestId,
                failureReason = "已取消",
                progressPercent = currentProgressPercent(songId, platform, requestId)
            )
            if (marked > 0) Result.failure() else Result.success()
        } catch (e: Exception) {
            storedArtifact?.let {
                cleanupStoredArtifact(context, it)
                storedArtifact = null
            }
            handleFailure(
                songId = songId,
                platform = platform,
                requestId = requestId,
                progressPercent = currentProgressPercent(songId, platform, requestId),
                failureReason = e.message ?: "下载失败",
                action = classifyDownloadFailure(throwable = e)
            )
        }
    }

    private suspend fun handleFailure(
        songId: String,
        platform: String,
        requestId: String,
        progressPercent: Int,
        failureReason: String,
        action: DownloadFailureAction
    ): Result {
        if (!isCurrentRequest(songId, platform, requestId)) {
            return Result.success()
        }

        return when (action) {
            DownloadFailureAction.RETRY -> Result.retry()
            DownloadFailureAction.FAIL -> {
                downloadedTracksDao.markFailed(songId, platform, requestId, failureReason, progressPercent)
                Result.failure()
            }
        }
    }

    private suspend fun isCurrentRequest(songId: String, platform: String, requestId: String): Boolean {
        return downloadedTracksDao.get(songId, platform)?.requestId == requestId
    }

    private suspend fun currentProgressPercent(songId: String, platform: String, requestId: String): Int {
        return downloadedTracksDao.get(songId, platform)
            ?.takeIf { it.requestId == requestId }
            ?.progressPercent
            ?.coerceIn(0, 99)
            ?: 0
    }

    private fun createForegroundInfo(title: String, artist: String): ForegroundInfo {
        val channelId = CHANNEL_ID
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("正在下载")
            .setContentText("$artist - $title")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_PLATFORM = "platform"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_QUALITY = "quality"
        const val KEY_PLAYABLE_URL = "playable_url"

        private const val CHANNEL_ID = "music_download_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

internal suspend fun persistCompletedDownload(
    appDatabase: AppDatabase,
    downloadedTracksDao: DownloadedTracksDao,
    localTracksDao: LocalTracksDao,
    songId: String,
    platform: String,
    requestId: String,
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    storedArtifact: StoredDownloadArtifact,
    downloadedAt: Long = System.currentTimeMillis()
) {
    appDatabase.withTransaction {
        localTracksDao.insertAll(
            listOf(
                LocalTrackEntity(
                    mediaStoreId = storedArtifact.mediaStoreId,
                    title = title.ifBlank { "未知歌曲" },
                    artist = artist.ifBlank { "未知艺术家" },
                    album = album,
                    durationMs = durationMs,
                    filePath = storedArtifact.playableUri,
                    fileSizeBytes = storedArtifact.fileSizeBytes,
                    mimeType = storedArtifact.mimeType
                )
            )
        )

        val updated = downloadedTracksDao.updateDownloadComplete(
            songId = songId,
            platform = platform,
            requestId = requestId,
            status = DownloadedTrackEntity.DownloadStatus.SUCCESS,
            filePath = storedArtifact.playableUri,
            fileSize = storedArtifact.fileSizeBytes,
            downloadedAt = downloadedAt
        )
        if (updated == 0) {
            throw StaleDownloadRequestException()
        }
    }
}

internal fun cleanupStoredArtifact(context: Context, storedArtifact: StoredDownloadArtifact) {
    deleteLocalPlayableUri(context, storedArtifact.playableUri)
}

internal class StaleDownloadRequestException : CancellationException("下载任务身份已失效")
