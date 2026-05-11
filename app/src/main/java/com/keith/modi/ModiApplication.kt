package com.keith.modi

import android.app.Application
import com.cloudinary.android.MediaManager

class ModiApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // PENDO: Initialize Supabase with Session Persistence
        Supabase.init(this)

        // PENDO: Initialize Cloudinary with Secure Configuration
        // Note: Production apps should use UNSIGNED uploads to avoid leaking API Secret.
        // Go to Cloudinary Settings > Upload > Add Upload Preset > Set mode to 'Unsigned'
        val config = mapOf(
            "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
            "api_key" to BuildConfig.CLOUDINARY_API_KEY
        )
        MediaManager.init(this, config)
    }
}
