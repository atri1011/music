package com.music.myapplication.data.repository

import com.music.myapplication.core.common.Result
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MetingPlayableResolverTest {

    @Test
    fun `NETEASE track - resolves redirected playable URL with mapped bitrate`() = runTest {
        val requestSlot = arrayOfNulls<Request>(1)
        val resolver = MetingPlayableResolver(
            client { request ->
                requestSlot[0] = request
                redirectResponse(request, "https://cdn.example.com/play.flac")
            }
        )

        val result = resolver.resolve(testTrack(Platform.NETEASE, id = "1969519579"), "flac")

        assertTrue(result is Result.Success)
        assertEquals("https://cdn.example.com/play.flac", (result as Result.Success).data)
        val request = requireNotNull(requestSlot[0])
        assertEquals("HEAD", request.method)
        assertEquals("netease", request.url.queryParameter("server"))
        assertEquals("url", request.url.queryParameter("type"))
        assertEquals("1969519579", request.url.queryParameter("id"))
        assertEquals("380", request.url.queryParameter("br"))
    }

    @Test
    fun `QQ numeric id - returns parse error without network`() = runTest {
        val calls = AtomicInteger(0)
        val resolver = MetingPlayableResolver(
            client { request ->
                calls.incrementAndGet()
                redirectResponse(request, "https://cdn.example.com/play.mp3")
            }
        )

        val result = resolver.resolve(testTrack(Platform.QQ, id = "123456"), "128k")

        assertTrue(result is Result.Error)
        assertEquals("Meting QQ 音源需要 songmid，当前歌曲仅有 songid", (result as Result.Error).error.message)
        assertEquals(0, calls.get())
    }

    @Test
    fun `KUWO track - returns unsupported error without network`() = runTest {
        val calls = AtomicInteger(0)
        val resolver = MetingPlayableResolver(
            client { request ->
                calls.incrementAndGet()
                redirectResponse(request, "https://cdn.example.com/play.mp3")
            }
        )

        val result = resolver.resolve(testTrack(Platform.KUWO), "128k")

        assertTrue(result is Result.Error)
        assertEquals("酷我暂不支持 Meting 音源", (result as Result.Error).error.message)
        assertEquals(0, calls.get())
    }

    @Test
    fun `429 response - returns rate limit error`() = runTest {
        val resolver = MetingPlayableResolver(
            client { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("".toResponseBody())
                    .build()
            }
        )

        val result = resolver.resolve(testTrack(Platform.NETEASE, id = "1969519579"), "128k")

        assertTrue(result is Result.Error)
        assertEquals("Meting 请求过于频繁，请 30 秒后重试", (result as Result.Error).error.message)
    }

    @Test
    fun `redirect without location - returns API error`() = runTest {
        val resolver = MetingPlayableResolver(
            client { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .body("".toResponseBody())
                    .build()
            }
        )

        val result = resolver.resolve(testTrack(Platform.NETEASE, id = "1969519579"), "320k")

        assertTrue(result is Result.Error)
        assertEquals("Meting 未返回可播放链接（HTTP 302）", (result as Result.Error).error.message)
    }

    private fun client(block: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain -> block(chain.request()) })
        .build()

    private fun redirectResponse(request: Request, location: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(302)
        .message("Found")
        .header("Location", location)
        .body("".toResponseBody())
        .build()

    private fun testTrack(platform: Platform, id: String = "track-1") = Track(
        id = id,
        platform = platform,
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美"
    )
}
