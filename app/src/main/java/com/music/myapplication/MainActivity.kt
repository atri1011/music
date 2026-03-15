package com.music.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.metrics.performance.JankStats
import com.music.myapplication.app.AppRoot
import com.music.myapplication.core.datastore.DarkModeOption
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.ui.theme.MusicAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var jankStats: JankStats? = null

    @Inject lateinit var preferences: PlayerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
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
            val darkModeOption by preferences.darkMode
                .collectAsStateWithLifecycle(initialValue = DarkModeOption.FOLLOW_SYSTEM)
            val darkTheme = when (darkModeOption) {
                DarkModeOption.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                DarkModeOption.DARK -> true
                DarkModeOption.LIGHT -> false
            }
            MusicAppTheme(darkTheme = darkTheme) {
                AppRoot()
            }
        }
    }

    override fun onDestroy() {
        jankStats = null
        super.onDestroy()
    }
}
