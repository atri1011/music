package com.music.myapplication.core.network.dispatch

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispatchTemplateCache @Inject constructor() {

    private val cache = ConcurrentHashMap<String, DispatchTemplate>()
    private val ttlMs = 30 * 60 * 1000L // 30 minutes

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

    fun clear() = cache.clear()
}
