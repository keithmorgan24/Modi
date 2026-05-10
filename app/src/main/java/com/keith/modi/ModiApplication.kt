package com.keith.modi

import android.app.Application
import com.cloudinary.android.MediaManager

class ModiApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // PENDO: Initialize Supabase with Session Persistence
        Supabase.init(this)

        // PENDO: Initialize Cloudinary for secure image handling
        val config = mapOf(
            "cloud_name" to "dtd34ejci",
            "api_key" to "575175727994488",
            "api_secret" to "zo4yjDQHq--0kITRsxmIBBbXBe0"
        )
        MediaManager.init(this, config)
    }
}
