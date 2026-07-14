package com.statproj.wifispatial.sensor

/**
 * Shared state manager for handover flags.
 * (Sensors have been removed in favor of Tap-to-Map).
 */
object SharedSensorManager {
    /**
     * Flag indicating that the user just finished defining corners and is transitioning
     * straight to data collection. The Collect screen can check this to auto-start.
     */
    var isHandoverToCollect: Boolean = false
    var handoverBuilding: String = ""
    var handoverFloor: String = ""
    
    val compassTracker = CompassTracker()
}
