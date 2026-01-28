package com.sb.arsketch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ArSketchApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber 초기화 (디버그 빌드에서만)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
