package com.music.myapplication.core.download

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStorageTest {

    @Test
    fun `infer extension and mime type from headers when url lacks suffix`() {
        assertEquals(
            "flac",
            inferDownloadExtension(
                contentTypeHeader = "audio/flac; charset=binary",
                requestUrl = "https://example.com/audio?id=1"
            )
        )
        assertEquals(
            "audio/flac",
            inferDownloadMimeType(
                contentTypeHeader = "audio/flac; charset=binary",
                requestUrl = "https://example.com/audio?id=1"
            )
        )
    }

    @Test
    fun `retry classification covers transient http and io failures`() {
        assertEquals(DownloadFailureAction.RETRY, classifyDownloadFailure(httpCode = 503))
        assertEquals(DownloadFailureAction.RETRY, classifyDownloadFailure(throwable = SocketTimeoutException("timeout")))
        assertEquals(DownloadFailureAction.RETRY, classifyDownloadFailure(throwable = IOException("network")))
        assertEquals(DownloadFailureAction.FAIL, classifyDownloadFailure(httpCode = 403))
    }

    @Test
    fun `sanitize file name strips unsafe characters and keeps extension`() {
        assertEquals(
            "歌手_ - 名称____song-1.mp3",
            sanitizeDownloadFileName(
                title = "名称:/?",
                artist = "歌手*",
                songId = "song-1",
                extension = "mp3"
            )
        )
    }
}
