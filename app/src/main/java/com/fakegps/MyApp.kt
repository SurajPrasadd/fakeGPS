package com.fakegps

import android.app.Application
import com.fakegps.AppConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Read contact email from assets/contact_email.txt (used in Nominatim User-Agent)
        AppConfig.init(this)          // or AppConfig.init(applicationContext)
    }
}