package com.music.myapplication.core.cache

import android.content.Context
import coil.imageLoader
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.database.dao.LyricsCacheDao
import com.music.myapplication.core.network.dispatch.DispatchTemplateCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private val dispatchTemplateCache: DispatchTemplateCache,
    private val playerPreferences: PlayerPreferences
) {

    suspend fun getUsage(): CacheUsage = withContext(Dispatchers.IO) {
        lyricsCacheDao.cleanExpired()
        collectUsage()
    }

    suspend fun enforceLimit(limitMb: Int? = null): CacheUsage = withContext(Dispatchers.IO) {
        lyricsCacheDao.cleanExpired()
        val cacheLimitBytes = normalizeLimitBytes(limitMb ?: playerPreferences.cacheLimitMb.first())

        dispatchTemplateCache.trimToSize(cacheLimitBytes)

        var usage = collectUsage()
        if (usage.totalBytes <= cacheLimitBytes) {
            return@withContext usage
        }

        trimLyricsCache(
            targetBytes = (cacheLimitBytes - usage.imageBytes - usage.templateBytes).coerceAtLeast(0L)
        )
        usage = collectUsage()
        if (usage.totalBytes <= cacheLimitBytes) {
            return@withContext usage
        }

        trimImageCache(
            targetBytes = (cacheLimitBytes - usage.lyricsBytes - usage.templateBytes).coerceAtLeast(0L)
        )

        collectUsage()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        context.imageLoader.memoryCache?.clear()
        imageCacheDir().clearDirectory()
        lyricsCacheDao.deleteAll()
        dispatchTemplateCache.clear()
    }

    private fun imageCacheDir(): File = File(context.cacheDir, "image_cache")

    private suspend fun collectUsage(): CacheUsage = CacheUsage(
        imageBytes = imageCacheDir().directorySize(),
        lyricsBytes = lyricsCacheDao.getActiveSizeBytes(),
        templateBytes = dispatchTemplateCache.sizeBytes()
    )

    private suspend fun trimLyricsCache(targetBytes: Long) {
        val entries = lyricsCacheDao.getActiveEntriesOrderedByUpdatedAt()
        if (entries.isEmpty()) return

        var currentBytes = entries.sumOf { it.lyricText.toByteArray().size.toLong() }
        if (currentBytes <= targetBytes) return

        val keysToDelete = mutableListOf<String>()
        for (entry in entries) {
            if (currentBytes <= targetBytes) break
            keysToDelete += entry.cacheKey
            currentBytes -= entry.lyricText.toByteArray().size.toLong()
        }
        if (keysToDelete.isNotEmpty()) {
            lyricsCacheDao.deleteByKeys(keysToDelete)
        }
    }

    private fun trimImageCache(targetBytes: Long) {
        val directory = imageCacheDir()
        if (!directory.exists()) return

        var currentBytes = directory.directorySize()
        if (currentBytes <= targetBytes) return

        val files = directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()

        var trimmed = false
        files.forEach { file ->
            if (currentBytes <= targetBytes) return@forEach
            val size = file.length()
            if (file.delete()) {
                currentBytes -= size
                trimmed = true
            }
        }

        if (trimmed) {
            context.imageLoader.memoryCache?.clear()
            directory.walkBottomUp()
                .filter { it.isDirectory && it != directory && it.listFiles().isNullOrEmpty() }
                .forEach { it.delete() }
        }
    }

    private fun normalizeLimitBytes(limitMb: Int): Long =
        limitMb
            .coerceIn(PlayerPreferences.MIN_CACHE_LIMIT_MB, PlayerPreferences.MAX_CACHE_LIMIT_MB)
            .toLong() * 1024L * 1024L

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
