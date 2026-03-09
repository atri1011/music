package com.music.myapplication.core.cache

import android.content.Context
import coil.imageLoader
import com.music.myapplication.core.database.dao.LyricsCacheDao
import com.music.myapplication.core.network.dispatch.DispatchTemplateCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CacheUsage(
    val imageBytes: Long = 0L,
    val lyricsBytes: Long = 0L,
    val templateBytes: Long = 0L
) {
    val totalBytes: Long
        get() = imageBytes + lyricsBytes + templateBytes
}

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lyricsCacheDao: LyricsCacheDao,
    private val dispatchTemplateCache: DispatchTemplateCache
) {

    suspend fun getUsage(): CacheUsage = withContext(Dispatchers.IO) {
        lyricsCacheDao.cleanExpired()
        CacheUsage(
            imageBytes = imageCacheDir().directorySize(),
            lyricsBytes = lyricsCacheDao.getActiveSizeBytes(),
            templateBytes = dispatchTemplateCache.sizeBytes()
        )
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        context.imageLoader.memoryCache?.clear()
        imageCacheDir().clearDirectory()
        lyricsCacheDao.deleteAll()
        dispatchTemplateCache.clear()
    }

    private fun imageCacheDir(): File = File(context.cacheDir, "image_cache")

    private fun File.clearDirectory() {
        if (!exists()) {
            mkdirs()
            return
        }
        listFiles()?.forEach { child ->
            if (child.isDirectory) {
                child.deleteRecursively()
            } else {
                child.delete()
            }
        }
    }

    private fun File.directorySize(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return walkTopDown()
            .filter { it.isFile }
            .sumOf(File::length)
    }
}
