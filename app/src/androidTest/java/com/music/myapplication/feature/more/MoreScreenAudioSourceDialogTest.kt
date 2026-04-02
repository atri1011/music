package com.music.myapplication.feature.more

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.music.myapplication.domain.model.AudioSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoreScreenAudioSourceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialog_shows_all_audio_sources_and_support_info() {
        val state = MoreUiState(audioSource = AudioSource.TUNEHUB)

        composeRule.setContent {
            MaterialTheme {
                AudioSourceSelectionDialog(
                    options = buildAudioSourceOptions(state),
                    onDismiss = {},
                    onSelect = {},
                    onConfigure = {}
                )
            }
        }

        composeRule.onNodeWithText("TuneHub").assertTextContains("TuneHub")
        composeRule.onNodeWithText("Meting (baka.plus)").assertTextContains("Meting (baka.plus)")
        composeRule.onNodeWithText("JKAPI (无铭API)").assertTextContains("JKAPI (无铭API)")
        composeRule.onNodeWithText("网易云增强版 API").assertTextContains("网易云增强版 API")
        composeRule.onNodeWithText("推荐/默认 · 当前使用中").assertTextContains("当前使用中")
        composeRule.onNodeWithText("额外配置：需 JKAPI 密钥（未配置）").assertTextContains("未配置")
        composeRule.onNodeWithText("额外配置：需增强版接口地址（未配置）").assertTextContains("未配置")
    }

    @Test
    fun dialog_routes_unconfigured_source_to_configuration_action() {
        val state = MoreUiState(audioSource = AudioSource.TUNEHUB)
        var configuredSource: AudioSource? = null

        composeRule.setContent {
            MaterialTheme {
                AudioSourceSelectionDialog(
                    options = buildAudioSourceOptions(state),
                    onDismiss = {},
                    onSelect = {},
                    onConfigure = { configuredSource = it }
                )
            }
        }

        composeRule.onNodeWithText("配置密钥").performClick()

        assertEquals(AudioSource.JKAPI, configuredSource)
    }

    @Test
    fun audio_source_subtitle_updates_when_selected_source_changes() {
        var state by mutableStateOf(MoreUiState(audioSource = AudioSource.TUNEHUB))

        composeRule.setContent {
            MaterialTheme {
                Text(audioSourceSubtitle(state))
            }
        }

        composeRule.onNodeWithText("当前：TuneHub（推荐/默认）", substring = true)
            .assertTextContains("TuneHub")

        composeRule.runOnUiThread {
            state = state.copy(audioSource = AudioSource.METING_BAKA)
        }

        composeRule.onNodeWithText("当前：Meting (baka.plus)", substring = true)
            .assertTextContains("Meting")
        composeRule.onNodeWithText("失败行为：不可用时自动回退 TuneHub", substring = true)
            .assertTextContains("自动回退 TuneHub")
    }
}
