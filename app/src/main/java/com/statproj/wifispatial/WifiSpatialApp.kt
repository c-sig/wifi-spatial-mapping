package com.statproj.wifispatial

import android.app.Application
import com.statproj.wifispatial.data.AppDatabase

/**
 * Application class that initializes the Room database lazily.
 */
class WifiSpatialApp : Application() {

    /** Lazily initialized database instance, accessible app-wide. */
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }
}
