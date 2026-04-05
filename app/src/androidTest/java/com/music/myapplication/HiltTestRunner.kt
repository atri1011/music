package com.music.myapplication

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader,
        className: String?,
        context: Context
    ): Application = super.newApplication(
        classLoader,
        "com.music.myapplication.MusicHiltTestApplication_Application",
        context
    )
}
