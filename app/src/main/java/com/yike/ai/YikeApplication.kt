package com.yike.ai

import android.app.Application
import com.yike.ai.di.AppContainer

class YikeApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Explicit startup is idempotent and also works before the manifest initializer runs.
        androidx.startup.AppInitializer.getInstance(this)
            .initializeComponent(androidx.work.WorkManagerInitializer::class.java)
            .cancelUniqueWork("daily-reminder")
    }
}
