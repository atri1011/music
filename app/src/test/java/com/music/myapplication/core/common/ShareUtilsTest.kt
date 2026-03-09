package com.music.myapplication.core.common

import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareUtilsTest {

    @Test
    fun buildTrackShareText_includesTrackMetadataAndOptionalLinks() {
        val track = Track(
            id = "185811",
            platform = Platform.NETEASE,
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美"
        )

        val text = ShareUtils.buildTrackShareText(
            track = track,
            options = TrackShareOptions(
                shareUrl = "https://example.com/share/185811",
                deepLink = "music://track/185811"
            )
        )

        assertTrue(text.contains("歌曲：晴天"))
        assertTrue(text.contains("歌手：周杰伦"))
        assertTrue(text.contains("专辑：叶惠美"))
        assertTrue(text.contains("平台：网易云"))
        assertTrue(text.contains("链接：https://example.com/share/185811"))
        assertTrue(text.contains("打开：music://track/185811"))
    }

    @Test
    fun buildTrackShareSubject_usesTitleAndArtist() {
        val track = Track(
            id = "0039MnYb0qxYhV",
            platform = Platform.QQ,
            title = "晴天",
            artist = "周杰伦"
        )

        assertEquals("晴天 - 周杰伦", ShareUtils.buildTrackShareSubject(track))
    }
}
