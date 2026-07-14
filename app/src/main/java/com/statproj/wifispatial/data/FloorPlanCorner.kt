package com.statproj.wifispatial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single corner point of a floor plan polygon.
 *
 * Corners are grouped by (building_pos, floor_lvl) and ordered by
 * [corner_order] to form the polygon outline used for the map visualization.
 * At least 3 corners are needed to form a valid polygon.
 */
@Entity(tableName = "floor_plan_corner")
data class FloorPlanCorner(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Building position identifier — must match WifiMeasurement.building_pos */
    val building_pos: String,

    /** Floor level identifier — must match WifiMeasurement.floor_lvl */
    val floor_lvl: String,

    /** PDR-derived X coordinate in meters */
    val x_coord: Double,

    /** PDR-derived Y coordinate in meters */
    val y_coord: Double,

    /** Ordering index (0-based) defining polygon vertex sequence */
    val corner_order: Int
)
