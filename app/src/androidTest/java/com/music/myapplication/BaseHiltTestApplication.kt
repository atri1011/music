package com.music.myapplication

import android.app.Application
import androidx.work.Configuration

abstract class BaseHiltTestApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
