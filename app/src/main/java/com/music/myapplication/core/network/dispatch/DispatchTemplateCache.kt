package com.music.myapplication.core.network.dispatch

import com.music.myapplication.data.remote.dto.MethodsDataDto
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class DispatchTemplateCache @Inject constructor() {

    private val cache = ConcurrentHashMap<String, DispatchTemplate>()
    private val ttlMs = 30 * 60 * 1000L // 30 minutes
    private val json = Json { encodeDefaults = true }

    fun get(platform: String, function: String): DispatchTemplate? {
        val key = "$platform:$function"
        val template = cache[key] ?: return null
        if (System.currentTimeMillis() - template.cachedAt > ttlMs) {
            cache.remove(key)
            return null
        }
        return template
    }

    fun put(platform: String, function: String, template: DispatchTemplate) {
        cache["$platform:$function"] = template
    }

    fun sizeBytes(now: Long = System.currentTimeMillis()): Long {
        evictExpired(now)
        return cache.values.sumOf { it.estimatedSizeBytes() }
    }

    fun trimToSize(maxBytes: Long, now: Long = System.currentTimeMillis()): Long {
        evictExpired(now)
        var currentSize = cache.values.sumOf { it.estimatedSizeBytes() }
        val boundedMaxBytes = maxBytes.coerceAtLeast(0L)
        if (currentSize <= boundedMaxBytes) return currentSize

        cache.entries
            .sortedBy { it.value.cachedAt }
            .forEach { entry ->
                if (currentSize <= boundedMaxBytes) return@forEach
                if (cache.remove(entry.key, entry.value)) {
                    currentSize -= entry.value.estimatedSizeBytes()
                }
            }
        return currentSize.coerceAtLeast(0L)
    }

    fun clear() = cache.clear()

    private fun evictExpired(now: Long) {
        cache.entries.removeIf { now - it.value.cachedAt > ttlMs }
    }

    private fun DispatchTemplate.estimatedSizeBytes(): Long {
        val payloadBytes = json.encodeToString(MethodsDataDto.serializer(), data).toByteArray().size.toLong()
        val keyBytes = (platform.length + function.length) * Char.SIZE_BYTES.toLong()
        return payloadBytes + keyBytes
    }
}
