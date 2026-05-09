package com.keith.modi

import android.app.Application
import com.cloudinary.android.MediaManager

class ModiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Cloudinary
        val config = mapOf(
            "cloud_name" to "dtd34ejci",
            "api_key" to "575175727994488",
            "api_secret" to "zo4yjDQHq--0kITRsxmIBBbXBe0", // Copy the full secret from your dashboard
            "secure" to true
        )
        MediaManager.init(this, config)
    }
}
