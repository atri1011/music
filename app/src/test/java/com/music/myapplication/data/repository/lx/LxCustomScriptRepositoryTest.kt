package com.music.myapplication.data.repository.lx

import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.LxScriptCatalogStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

class LxCustomScriptRepositoryTest {

    @Test
    fun importScriptFromUrl_downloadsHttpsScriptAndStripsBom() = runTest {
        val repository = repository(
            client { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("\uFEFFconsole.log('lx');".toResponseBody())
                    .build()
            }
        )

        val result = repository.importScriptFromUrl(" https://example.com/scripts/demo.js ")

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals("console.log('lx');", data.rawScript)
        assertEquals("demo.js", data.sourceLabel)
    }

    @Test
    fun importScriptFromUrl_usesFinalResponseUrlAsSourceLabel() = runTest {
        val repository = repository(
            client {
                val finalRequest = Request.Builder()
                    .url("https://cdn.example.com/lx/final-source.js")
                    .get()
                    .build()
                Response.Builder()
                    .request(finalRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("console.log('redirect');".toResponseBody())
                    .build()
            }
        )

        val result = repository.importScriptFromUrl("https://example.com/redirect")

        assertTrue(result is Result.Success)
        assertEquals("final-source.js", (result as Result.Success).data.sourceLabel)
    }

    @Test
    fun importScriptFromUrl_rejectsInvalidOrNonHttpsUrlWithoutNetwork() = runTest {
        val calls = AtomicInteger(0)
        val repository = repository(
            client { request ->
                calls.incrementAndGet()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("console.log('lx');".toResponseBody())
                    .build()
            }
        )

        val result = repository.importScriptFromUrl("http://example.com/demo.js")

        assertTrue(result is Result.Error)
        assertEquals("请输入有效的 https 脚本链接", (result as Result.Error).error.message)
        assertEquals(0, calls.get())
    }

    @Test
    fun importScriptFromUrl_returnsNetworkErrorForNonSuccessResponse() = runTest {
        val repository = repository(
            client { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody())
                    .build()
            }
        )

        val result = repository.importScriptFromUrl("https://example.com/missing.js")

        assertTrue(result is Result.Error)
        assertEquals("下载脚本失败（HTTP 404）", (result as Result.Error).error.message)
    }

    @Test
    fun importScriptFromUrl_returnsParseErrorForBlankBody() = runTest {
        val repository = repository(
            client { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
        )

        val result = repository.importScriptFromUrl("https://example.com/empty.js")

        assertTrue(result is Result.Error)
        assertEquals("导入失败：链接返回的脚本内容为空", (result as Result.Error).error.message)
    }

    private fun repository(client: OkHttpClient): LxCustomScriptRepository {
        val store = mockk<LxScriptCatalogStore>(relaxed = true)
        val runtime = mockk<LxCustomScriptRuntime>(relaxed = true)
        every { store.catalog } returns MutableStateFlow(LxScriptCatalog())
        return LxCustomScriptRepository(store, runtime, client)
    }

    private fun client(block: (Request) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain -> block(chain.request()) })
        .build()
}
