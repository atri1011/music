package com.music.myapplication.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.music.myapplication.R
import com.music.myapplication.core.database.dao.DownloadedTracksDao
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val downloadedTracksDao: DownloadedTracksDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val platform = inputData.getString(KEY_PLATFORM) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: ""
        val artist = inputData.getString(KEY_ARTIST) ?: ""
        val album = inputData.getString(KEY_ALBUM) ?: ""
        val coverUrl = inputData.getString(KEY_COVER_URL) ?: ""
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val quality = inputData.getString(KEY_QUALITY) ?: "128k"
        val playableUrl = inputData.getString(KEY_PLAYABLE_URL) ?: return@withContext Result.failure()

        // Insert initial record as downloading
        downloadedTracksDao.insert(
            DownloadedTrackEntity(
                songId = songId,
                platform = platform,
                title = title,
                artist = artist,
                album = album,
                coverUrl = coverUrl,
                durationMs = durationMs,
                quality = quality,
                downloadStatus = DownloadedTrackEntity.DownloadStatus.DOWNLOADING
            )
        )

        try {
            setForeground(createForegroundInfo(title, artist))

            val downloadDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
            val safeTitle = title.replace(Regex("[^\\w\\u4e00-\\u9fff\\-. ]"), "_")
            val safeArtist = artist.replace(Regex("[^\\w\\u4e00-\\u9fff\\-. ]"), "_")
            val fileName = "${safeArtist} - ${safeTitle}_${songId}.mp3"
            val outputFile = File(downloadDir, fileName)

            val request = Request.Builder().url(playableUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                downloadedTracksDao.updateStatus(songId, platform, DownloadedTrackEntity.DownloadStatus.FAILED)
                return@withContext Result.failure()
            }

            response.body?.byteStream()?.use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: run {
                downloadedTracksDao.updateStatus(songId, platform, DownloadedTrackEntity.DownloadStatus.FAILED)
                return@withContext Result.failure()
            }

            downloadedTracksDao.updateDownloadComplete(
                songId = songId,
                platform = platform,
                status = DownloadedTrackEntity.DownloadStatus.SUCCESS,
                filePath = outputFile.absolutePath,
                fileSize = outputFile.length()
            )

            Result.success()
        } catch (e: Exception) {
            downloadedTracksDao.updateStatus(songId, platform, DownloadedTrackEntity.DownloadStatus.FAILED)
            Result.failure()
        }
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

        return ForegroundInfo(NOTIFICATION_ID, notification)
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
