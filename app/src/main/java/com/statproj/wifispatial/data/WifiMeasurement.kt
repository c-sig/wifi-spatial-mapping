package com.statproj.wifispatial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single Wi-Fi spatial measurement point.
 *
 * Each row captures one "stop & test" data point including:
 * - Location context (building, floor)
 * - Wi-Fi signal strength (dBm)
 * - Speed test results (download/upload Mbps)
 * - PDR-derived spatial coordinates (x, y)
 */
@Entity(tableName = "wifi_measurement")
data class WifiMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Unix timestamp in milliseconds */
    val timestamp: Long,

    /** Independent variable A: building position identifier */
    val building_pos: String,

    /** Independent variable B: floor level identifier */
    val floor_lvl: String,

    /** Wi-Fi RSSI in dBm */
    val wifi_dbm: Int,

    /** Download speed in Mbps (null if test failed) */
    val dl_mbps: Double?,

    /** Upload speed in Mbps (null if test failed) */
    val ul_mbps: Double?,

    /** PDR-derived X coordinate in meters (relative to session start) */
    val x_coord: Double,

    /** PDR-derived Y coordinate in meters (relative to session start) */
    val y_coord: Double
)
