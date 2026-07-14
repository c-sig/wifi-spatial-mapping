package com.statproj.wifispatial.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FloorPlanCorner table.
 * Provides queries for corner CRUD operations and prerequisite checks.
 */
@Dao
interface FloorPlanCornerDao {

    // ── Insert ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(corner: FloorPlanCorner): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(corners: List<FloorPlanCorner>)

    // ── Querying ────────────────────────────────────────────────────────

    /** Get corners for a specific (building, floor) group, ordered by corner_order */
    @Query("SELECT * FROM floor_plan_corner WHERE building_pos = :building AND floor_lvl = :floor ORDER BY corner_order ASC")
    suspend fun getByGroup(building: String, floor: String): List<FloorPlanCorner>

    /** Observe corners reactively for a specific group */
    @Query("SELECT * FROM floor_plan_corner WHERE building_pos = :building AND floor_lvl = :floor ORDER BY corner_order ASC")
    fun observeByGroup(building: String, floor: String): Flow<List<FloorPlanCorner>>

    /** Count corners for a specific group (prerequisite check) */
    @Query("SELECT COUNT(*) FROM floor_plan_corner WHERE building_pos = :building AND floor_lvl = :floor")
    suspend fun countByGroup(building: String, floor: String): Int

    /** Observe corner count reactively */
    @Query("SELECT COUNT(*) FROM floor_plan_corner WHERE building_pos = :building AND floor_lvl = :floor")
    fun observeCountByGroup(building: String, floor: String): Flow<Int>

    /** Get all corners */
    @Query("SELECT * FROM floor_plan_corner ORDER BY building_pos, floor_lvl, corner_order")
    suspend fun getAll(): List<FloorPlanCorner>

    // ── Deletion ────────────────────────────────────────────────────────

    @Query("DELETE FROM floor_plan_corner WHERE building_pos = :building AND floor_lvl = :floor")
    suspend fun deleteByGroup(building: String, floor: String)

    @Query("DELETE FROM floor_plan_corner")
    suspend fun deleteAll()
}
