package com.miclink

import android.app.Application
import android.util.Log

/**
 * MicLink Application
 */
class MicLinkApp : Application() {
    
    companion object {
        private const val TAG = "MicLinkApp"
        lateinit var instance: MicLinkApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MicLink Application started")
    }
}
