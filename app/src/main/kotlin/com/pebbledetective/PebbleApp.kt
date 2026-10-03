package com.pebbledetective

import android.app.Application
import com.pebbledetective.core.AppContainer

class PebbleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
