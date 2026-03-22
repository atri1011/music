package com.music.myapplication.core.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.database.dao.DownloadedTracksDao
import com.music.myapplication.core.database.dao.LocalTracksDao
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import com.music.myapplication.core.database.mapper.toDownloadedTrackEntity
import com.music.myapplication.core.network.NetworkMonitor
import com.music.myapplication.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedTracksDao: DownloadedTracksDao,
    private val localTracksDao: LocalTracksDao,
    private val preferences: PlayerPreferences,
    private val networkMonitor: NetworkMonitor
) {
    private val workManager = WorkManager.getInstance(context)

    fun hasRequiredDownloadPermission(): Boolean = hasLegacyPublicWritePermission(context)

    suspend fun shouldWaitForUnmeteredNetwork(): Boolean =
        preferences.wifiOnly.first() && !networkMonitor.isUnmeteredConnection()

    suspend fun enqueueDownload(track: Track, playableUrl: String, quality: String): Boolean = withContext(Dispatchers.IO) {
        val existing = downloadedTracksDao.get(track.id, track.platform.id)
        if (existing?.downloadStatus == DownloadedTrackEntity.DownloadStatus.SUCCESS) {
            if (reconcileSuccessfulTrack(existing) != null) {
                return@withContext false
            }
        }
        if (existing?.downloadStatus == DownloadedTrackEntity.DownloadStatus.DOWNLOADING) {
            if (isWorkInProgress(track.id, track.platform.id)) {
                return@withContext false
            }
            downloadedTracksDao.markFailed(
                track.id,
                track.platform.id,
                existing.requestId,
                "下载任务异常中断",
                existing.progressPercent.coerceAtLeast(0)
            )
        }

        val workData = Data.Builder()
            .putString(DownloadWorker.KEY_SONG_ID, track.id)
            .putString(DownloadWorker.KEY_PLATFORM, track.platform.id)
            .putString(DownloadWorker.KEY_TITLE, track.title)
            .putString(DownloadWorker.KEY_ARTIST, track.artist)
            .putString(DownloadWorker.KEY_ALBUM, track.album)
            .putString(DownloadWorker.KEY_COVER_URL, track.coverUrl)
            .putLong(DownloadWorker.KEY_DURATION_MS, track.durationMs)
            .putString(DownloadWorker.KEY_QUALITY, quality)
            .putString(DownloadWorker.KEY_PLAYABLE_URL, playableUrl)
            .build()

        val requiredNetworkType = if (preferences.wifiOnly.first()) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetworkType)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag(TAG_DOWNLOAD)
            .addTag(workTag(track.id, track.platform.id))
            .build()
        val requestId = workRequest.id.toString()

        downloadedTracksDao.insert(
            track.copy(quality = quality).toDownloadedTrackEntity(
                progressPercent = 0,
                failureReason = "",
                requestId = requestId,
                status = DownloadedTrackEntity.DownloadStatus.DOWNLOADING
            )
        )

        val uniqueWorkName = workName(track.id, track.platform.id)
        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequest)
        return@withContext true
    }

    suspend fun cancelDownload(songId: String, platform: String) = withContext(Dispatchers.IO) {
        val entity = downloadedTracksDao.get(songId, platform)
        workManager.cancelUniqueWork(workName(songId, platform))
        if (entity == null) return@withContext
        downloadedTracksDao.markFailed(
            songId = songId,
            platform = platform,
            requestId = entity.requestId,
            failureReason = "已取消",
            progressPercent = entity.progressPercent.coerceIn(0, 99)
        )
    }

    suspend fun removeDownloaded(songId: String, platform: String) = withContext(Dispatchers.IO) {
        deleteDownloadRecord(songId, platform)
    }

    suspend fun isDownloaded(songId: String, platform: String): Boolean =
        downloadedTracksDao.isDownloaded(songId, platform)

    fun getDownloadedTracks(): Flow<List<DownloadedTrackEntity>> =
        downloadedTracksDao.getDownloaded()

    fun getAllTracks(): Flow<List<DownloadedTrackEntity>> =
        downloadedTracksDao.getAll()

    fun getDownloadingTracks(): Flow<List<DownloadedTrackEntity>> =
        downloadedTracksDao.getDownloading()

    suspend fun reconcileTrackedDownloads() = withContext(Dispatchers.IO) {
        reconcileStaleDownloadingTracks()
        reconcileMissingSuccessfulTracks()
    }

    private suspend fun reconcileStaleDownloadingTracks() {
        val downloadingTracks = downloadedTracksDao.getDownloadingNow()
        downloadingTracks.forEach { track ->
            if (!isWorkInProgress(track.songId, track.platform)) {
                downloadedTracksDao.markFailed(
                    track.songId,
                    track.platform,
                    track.requestId,
                    "下载任务异常中断",
                    progressPercent = track.progressPercent.coerceAtLeast(0)
                )
            }
        }
    }

    private suspend fun reconcileMissingSuccessfulTracks() {
        downloadedTracksDao.getDownloadedNow().forEach { track ->
            reconcileSuccessfulTrack(track)
        }
    }

    fun getDownloadedCount(): Flow<Int> =
        downloadedTracksDao.countDownloaded()

    suspend fun getDownloadedFilePath(songId: String, platform: String): String? {
        val entity = downloadedTracksDao.get(songId, platform)
        return if (entity?.downloadStatus == DownloadedTrackEntity.DownloadStatus.SUCCESS) {
            reconcileSuccessfulTrack(entity)
        } else null
    }

    fun observeWorkState(songId: String, platform: String): Flow<Boolean> {
        return workManager.getWorkInfosForUniqueWorkFlow(workName(songId, platform))
            .map { workInfoList ->
                workInfoList.any { isActiveState(it.state) }
            }
    }

    private suspend fun isWorkInProgress(songId: String, platform: String): Boolean {
        val workInfoList = workManager.getWorkInfosForUniqueWorkFlow(workName(songId, platform)).first()
        return workInfoList.any { isActiveState(it.state) }
    }

    private fun isActiveState(state: WorkInfo.State): Boolean {
        return state == WorkInfo.State.RUNNING ||
            state == WorkInfo.State.ENQUEUED ||
            state == WorkInfo.State.BLOCKED
    }

    private suspend fun reconcileSuccessfulTrack(entity: DownloadedTrackEntity): String? {
        val localUri = entity.filePath.trim()
        if (localUri.isNotBlank() && isLocalPlayableUriAvailable(context, localUri)) {
            return localUri
        }

        removeLocalTrackRecord(localUri)
        downloadedTracksDao.markFailed(
            songId = entity.songId,
            platform = entity.platform,
            requestId = entity.requestId,
            failureReason = "本地文件已丢失，请重新下载"
        )
        return null
    }

    private suspend fun deleteDownloadRecord(songId: String, platform: String) {
        val entity = downloadedTracksDao.get(songId, platform)
        entity?.filePath?.takeIf { it.isNotBlank() }?.let { filePath ->
            removeLocalTrackRecord(filePath)
            deleteLocalPlayableUri(context, filePath)
        }
        downloadedTracksDao.deleteBySongIdAndPlatform(songId, platform)
    }

    private suspend fun removeLocalTrackRecord(filePath: String) {
        val mediaStoreId = mediaStoreIdFromPlayableUri(filePath) ?: return
        localTracksDao.deleteByIds(listOf(mediaStoreId))
    }

    private fun workName(songId: String, platform: String) = "download_${platform}_${songId}"
    private fun workTag(songId: String, platform: String) = "download:${platform}:${songId}"

    companion object {
        const val TAG_DOWNLOAD = "music_download"
    }
}
