package com.keith.modi

import android.app.Application
import com.cloudinary.android.MediaManager

class ModiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Cloudinary
        val config = mapOf(
            "cloud_name" to "dtd34ejci",
            "secure" to true
        )
        MediaManager.init(this, config)
    }
}
