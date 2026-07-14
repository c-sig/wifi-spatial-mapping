package com.statproj.wifispatial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.statproj.wifispatial.ui.navigation.AppNavGraph
import com.statproj.wifispatial.ui.theme.WifiSpatialTheme

/**
 * Single-activity entry point for the Wi-Fi Spatial app.
 * Uses Jetpack Compose with Navigation for all screens.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WifiSpatialTheme {
                AppNavGraph()
            }
        }
    }
}
