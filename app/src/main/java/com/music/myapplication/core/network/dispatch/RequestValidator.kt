package com.music.myapplication.core.network.dispatch

import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestValidator @Inject constructor() {

    private val allowedHosts = setOf(
        "music.163.com",
        "interface.music.163.com",
        "interface3.music.163.com",
        "y.qq.com",
        "u.y.qq.com",
        "c.y.qq.com",
        "dl.stream.qqmusic.qq.com",
        "kuwo.cn",
        "www.kuwo.cn",
        "api.kuwo.cn",
        "nmobi.kuwo.cn",
        "artistpicserver.kuwo.cn"
    )

    fun validate(url: String): Boolean {
        return try {
            val parsed = URL(url)
            (parsed.protocol == "https" || parsed.protocol == "http") && allowedHosts.any { host ->
                parsed.host == host || parsed.host.endsWith(".$host")
            }
        } catch (_: Exception) {
            false
        }
    }
}
