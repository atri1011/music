package com.music.myapplication.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "MusicPlayer/1.0 Android")
            .build()
        return chain.proceed(request)
    }
}
