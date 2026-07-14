package com.statproj.wifispatial.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for WifiMeasurement table.
 * Provides all queries needed for data collection, reporting, and export.
 */
@Dao
interface WifiMeasurementDao {

    // ── Insert ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: WifiMeasurement): Long

    // ── Delete ──────────────────────────────────────────────────────────

    /** Delete the most recently collected measurement for a specific group */
    @Query("DELETE FROM wifi_measurement WHERE id = (SELECT id FROM wifi_measurement WHERE building_pos = :building AND floor_lvl = :floor ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastMeasurementByGroup(building: String, floor: String)

    // ── Counting (for prescribed sample size enforcement) ───────────────

    /** Count measurements for a specific (building, floor) group */
    @Query("SELECT COUNT(*) FROM wifi_measurement WHERE building_pos = :building AND floor_lvl = :floor")
    suspend fun countByGroup(building: String, floor: String): Int

    /** Observe count changes reactively */
    @Query("SELECT COUNT(*) FROM wifi_measurement WHERE building_pos = :building AND floor_lvl = :floor")
    fun observeCountByGroup(building: String, floor: String): Flow<Int>

    // ── Querying ────────────────────────────────────────────────────────

    /** Get all measurements ordered by timestamp */
    @Query("SELECT * FROM wifi_measurement ORDER BY timestamp ASC")
    suspend fun getAll(): List<WifiMeasurement>

    /** Observe all measurements reactively */
    @Query("SELECT * FROM wifi_measurement ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<WifiMeasurement>>

    /** Get measurements for a specific (building, floor) group */
    @Query("SELECT * FROM wifi_measurement WHERE building_pos = :building AND floor_lvl = :floor ORDER BY timestamp ASC")
    suspend fun getByGroup(building: String, floor: String): List<WifiMeasurement>

    /** Get all measurements for the current session's (building, floor) for proximity checks */
    @Query("SELECT x_coord, y_coord FROM wifi_measurement WHERE building_pos = :building AND floor_lvl = :floor")
    suspend fun getCoordinatesByGroup(building: String, floor: String): List<CoordinatePoint>

    /** Get distinct building values */
    @Query("SELECT DISTINCT building_pos FROM wifi_measurement")
    suspend fun getDistinctBuildings(): List<String>

    /** Get distinct floor values */
    @Query("SELECT DISTINCT floor_lvl FROM wifi_measurement")
    suspend fun getDistinctFloors(): List<String>

    /** Get total measurement count */
    @Query("SELECT COUNT(*) FROM wifi_measurement")
    suspend fun getTotalCount(): Int

    // ── Deletion ────────────────────────────────────────────────────────

    @Query("DELETE FROM wifi_measurement")
    suspend fun deleteAll()

    @Query("DELETE FROM wifi_measurement WHERE building_pos = :building AND floor_lvl = :floor")
    suspend fun deleteByGroup(building: String, floor: String)
}

/**
 * Lightweight projection for coordinate-only queries (proximity checks).
 */
data class CoordinatePoint(
    val x_coord: Double,
    val y_coord: Double
)
