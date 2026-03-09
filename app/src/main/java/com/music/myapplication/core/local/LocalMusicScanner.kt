package com.music.myapplication.core.local

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.music.myapplication.core.database.dao.LocalTracksDao
import com.music.myapplication.core.database.entity.LocalTrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class LocalTrackSyncPlan(
    val upserts: List<LocalTrackEntity>,
    val deleteIds: List<Long>
)

internal fun buildLocalTrackSyncPlan(
    existing: List<LocalTrackEntity>,
    scanned: List<LocalTrackEntity>
): LocalTrackSyncPlan {
    val existingById = existing.associateBy(LocalTrackEntity::mediaStoreId)
    val scannedById = scanned.associateBy(LocalTrackEntity::mediaStoreId)

    val upserts = scanned.filter { entity ->
        existingById[entity.mediaStoreId] != entity
    }
    val deleteIds = existingById.keys
        .subtract(scannedById.keys)
        .toList()

    return LocalTrackSyncPlan(
        upserts = upserts,
        deleteIds = deleteIds
    )
}

@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localTracksDao: LocalTracksDao
) {
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val scannedTracks = scanMediaStore()
        val syncPlan = buildLocalTrackSyncPlan(
            existing = localTracksDao.getAllOnce(),
            scanned = scannedTracks
        )

        if (syncPlan.upserts.isNotEmpty()) {
            localTracksDao.insertAll(syncPlan.upserts)
        }
        if (syncPlan.deleteIds.isNotEmpty()) {
            localTracksDao.deleteByIds(syncPlan.deleteIds)
        }

        scannedTracks.size
    }

    private fun scanMediaStore(): List<LocalTrackEntity> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        ) ?: throw IllegalStateException("读取系统媒体库失败")

        return cursor.use { queryCursor ->
            val scannedTracks = mutableListOf<LocalTrackEntity>()
            val idIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val addedAtIndex = queryCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (queryCursor.moveToNext()) {
                val mediaStoreId = queryCursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaStoreId
                ).toString()
                scannedTracks += LocalTrackEntity(
                    mediaStoreId = mediaStoreId,
                    title = queryCursor.getStringOrEmpty(titleIndex).ifBlank { "未知歌曲" },
                    artist = queryCursor.getStringOrEmpty(artistIndex).normalizeArtist(),
                    album = queryCursor.getStringOrEmpty(albumIndex),
                    durationMs = queryCursor.getLongOrZero(durationIndex),
                    filePath = contentUri,
                    fileSizeBytes = queryCursor.getLongOrZero(sizeIndex),
                    mimeType = queryCursor.getStringOrEmpty(mimeTypeIndex),
                    addedAt = queryCursor.getLongOrZero(addedAtIndex)
                        .takeIf { value -> value > 0L }
                        ?.times(1000L)
                        ?: 0L
                )
            }
            scannedTracks
        }
    }

    companion object {
        private const val MIN_DURATION_MS = 30_000L
    }
}

private fun Cursor.getStringOrEmpty(index: Int): String =
    if (isNull(index)) "" else getString(index).orEmpty()

private fun Cursor.getLongOrZero(index: Int): Long =
    if (isNull(index)) 0L else getLong(index)

private fun String.normalizeArtist(): String {
    val normalized = trim()
    return when {
        normalized.isBlank() -> "未知艺术家"
        normalized.equals("<unknown>", ignoreCase = true) -> "未知艺术家"
        else -> normalized
    }
}
