package com.music.myapplication.core.network.interceptor

import com.music.myapplication.core.datastore.PlayerPreferences
import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(
    private val preferences: PlayerPreferences,
    private val fallbackApiKey: String = ""
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = preferences.currentApiKey.ifBlank { fallbackApiKey }.trim()
        if (apiKey.isBlank()) return chain.proceed(request)

        val isTuneHubRequest = request.url.host == "tunehub.sayqz.com" &&
            request.url.encodedPath.startsWith("/api/v1/")
        if (isTuneHubRequest) {
            val newRequest = request.newBuilder()
                .header("X-API-Key", apiKey)
                .build()
            return chain.proceed(newRequest)
        }
        return chain.proceed(request)
    }
}
