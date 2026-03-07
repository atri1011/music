package com.music.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.metrics.performance.JankStats
import com.music.myapplication.app.AppRoot
import com.music.myapplication.ui.theme.MusicAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) {
            jankStats = JankStats.createAndTrack(window) { frameData ->
                if (frameData.isJank) {
                    Log.d(
                        "JankStats",
                        "Jank frame: ${frameData.frameDurationUiNanos / 1_000_000f}ms states=${frameData.states}"
                    )
                }
            }
        }
        setContent {
            MusicAppTheme {
                AppRoot()
            }
        }
    }

    override fun onDestroy() {
        jankStats = null
        super.onDestroy()
    }
}
