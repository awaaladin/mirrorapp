package com.mirror.app

import android.app.Application
import com.mirror.app.core.crash.CrashReporter

class MirrorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
