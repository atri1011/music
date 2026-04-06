package com.music.myapplication.data.repository.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LxCustomScriptMetadataParserTest {

    @Test
    fun parseLxScriptMetadata_supportsBangPrefixedCommentHeader() {
        val rawScript = """
            /*!
             * @name [独家音源]
             * @description 音源更新
             * @version 4
             * @author 洛雪科技
             * @repository https://github.com/lxmusics/lx-music-api-server
             */
            ;(function () {})()
        """.trimIndent()

        val metadata = parseLxScriptMetadata(
            scriptId = "demo-script",
            rawScript = rawScript
        )

        assertNotNull(metadata)
        assertEquals("[独家音源]", metadata?.name)
        assertEquals("音源更新", metadata?.description)
        assertEquals("4", metadata?.version)
        assertEquals("洛雪科技", metadata?.author)
    }

    @Test
    fun parseLxScriptMetadata_skipsLeadingNonMetadataCommentBlock() {
        val rawScript = """
            /**
             * 这是许可证说明
             * @license MIT
             */
            /*!
             * @name 测试脚本
             * @description 第二段注释才是真正元信息
             */
            ;(function () {})()
        """.trimIndent()

        val metadata = parseLxScriptMetadata(
            scriptId = "script-with-license",
            rawScript = rawScript
        )

        assertNotNull(metadata)
        assertEquals("测试脚本", metadata?.name)
        assertEquals("第二段注释才是真正元信息", metadata?.description)
    }
}
