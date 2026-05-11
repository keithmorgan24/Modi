package com.keith.modi

import android.app.Application
import com.cloudinary.android.MediaManager

class ModiApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // PENDO: Initialize Supabase with Session Persistence
        Supabase.init(this)

        // PENDO: Initialize Cloudinary with Secure Configuration
        // For unsigned uploads, we only need the cloud_name. 
        // Providing api_key can sometimes trigger 401 errors if not correctly validated.
        val config = mapOf(
            "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME
        )
        MediaManager.init(this, config)
    }
}
