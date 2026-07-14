package com.statproj.wifispatial.debug

import android.content.Context
import android.widget.Toast
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.viewmodel.ConfigViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import java.util.Random

/**
 * Simulation engine for testing the app without physical hardware.
 *
 * Generates realistic-looking fake data that exercises all app logic:
 * - Proximity checks (points spaced > 3m apart)
 * - Sample counting (fills to 30/group)
 * - 5GHz frequency filter (occasionally generates 2.4GHz to test rejection)
 * - Speed test results (varied but realistic Mbps values)
 * - Statistical analysis (data with known effects for ANOVA validation)
 *
 * Usage:
 *   SimulationEngine.populateGroup(context, "North", "Floor 1", count = 30)
 *   SimulationEngine.populateAll(context)     // fills all 20 cells
 *   SimulationEngine.populateWithKnownEffects(context)  // known statistical effects
 */
object SimulationEngine {

    private const val TAG = "SimulationEngine"
    private val rng = Random()

    private fun getHallwayPath(building: String): Pair<List<Pair<Double, Double>>, List<Pair<Double, Double>>> {
        val width = 2.0 // half-width of hallway
        return when (building) {
            "West" -> {
                // Long straight hallway
                val spine = listOf(0.0 to 0.0, 40.0 to 0.0)
                val corners = listOf(
                    0.0 to -width, 40.0 to -width, 40.0 to width, 0.0 to width
                )
                corners to spine
            }
            "North", "South" -> {
                // Upright straight hallway
                val spine = listOf(0.0 to 0.0, 0.0 to 40.0)
                val corners = listOf(
                    -width to 0.0, -width to 40.0, width to 40.0, width to 0.0
                )
                corners to spine
            }
            "South West" -> {
                // Straight with branch (L-shaped)
                val spine = listOf(0.0 to 0.0, 30.0 to 0.0, 30.0 to 20.0)
                val corners = listOf(
                    0.0 to -width, 30.0 + width to -width, 30.0 + width to 20.0,
                    30.0 - width to 20.0, 30.0 - width to width, 0.0 to width
                )
                corners to spine
            }
            "North" -> {
                // Upright straight hallway
                val spine = listOf(0.0 to 0.0, 0.0 to 40.0)
                val corners = listOf(
                    -width to 0.0, -width to 40.0, width to 40.0, width to 0.0
                )
                corners to spine
            }
            "North West" -> {
                // Another L-shape
                val spine = listOf(0.0 to 20.0, 0.0 to 0.0, 20.0 to 0.0)
                val corners = listOf(
                    -width to 20.0, width to 20.0, width to width,
                    20.0 to width, 20.0 to -width, -width to -width
                )
                corners to spine
            }
            else -> {
                val spine = listOf(0.0 to 0.0, 30.0 to 0.0)
                val corners = listOf(0.0 to -width, 30.0 to -width, 30.0 to width, 0.0 to width)
                corners to spine
            }
        }
    }

    private fun getPointAlongSpine(spine: List<Pair<Double, Double>>, fraction: Double): Pair<Double, Double> {
        if (spine.isEmpty()) return 0.0 to 0.0
        if (spine.size == 1) return spine[0]
        
        var totalLength = 0.0
        val segmentLengths = mutableListOf<Double>()
        for (i in 0 until spine.size - 1) {
            val (x1, y1) = spine[i]
            val (x2, y2) = spine[i+1]
            val dist = Math.hypot(x2 - x1, y2 - y1)
            segmentLengths.add(dist)
            totalLength += dist
        }
        
        val targetDist = fraction * totalLength
        var currentDist = 0.0
        
        for (i in 0 until spine.size - 1) {
            val segLen = segmentLengths[i]
            if (currentDist + segLen >= targetDist || i == spine.size - 2) {
                val segFraction = if (segLen == 0.0) 0.0 else (targetDist - currentDist) / segLen
                val (x1, y1) = spine[i]
                val (x2, y2) = spine[i+1]
                val px = x1 + (x2 - x1) * segFraction
                val py = y1 + (y2 - y1) * segFraction
                return px to py
            }
            currentDist += segLen
        }
        return spine.last()
    }

    /**
     * Populate a single (building, floor) group with [count] fake measurements.
     * Points are placed in a grid pattern guaranteeing > 3m spacing.
     */
    suspend fun populateGroup(
        context: Context,
        building: String,
        floor: String,
        count: Int = 30
    ): Int {
        val dao = (context.applicationContext as WifiSpatialApp).database.wifiMeasurementDao()
        val existingCount = dao.countByGroup(building, floor)
        val remaining = (count - existingCount).coerceAtLeast(0)

        if (remaining == 0) return existingCount

        var inserted = 0
        val (_, spine) = getHallwayPath(building)
        for (i in 0 until remaining) {
            // Place points along the hallway spine with some noise
            val fraction = i.toDouble() / remaining.coerceAtLeast(1)
            val (spineX, spineY) = getPointAlongSpine(spine, fraction)
            val x = spineX + rng.nextGaussian() * 0.6
            val y = spineY + rng.nextGaussian() * 0.6

            // Distance to nearest AP (at fraction 0.0 or 1.0)
            val distToAp = minOf(fraction, 1.0 - fraction) // 0.0 to 0.5
            val signalDrop = distToAp * 2.0 * 30.0 // drops up to 30 dBm in the middle

            // Simulate realistic Wi-Fi values with floor/building effects
            val floorIndex = ConfigViewModel.FLOORS.indexOf(floor)
            val buildingIndex = ConfigViewModel.BUILDINGS.indexOf(building)

            // Signal gets weaker on higher floors (attenuation)
            val baseDbm = -35.0 - (floorIndex * 5.0) - (buildingIndex * 3.0) - signalDrop
            val dbm = (baseDbm + rng.nextGaussian() * 3.0).toInt().coerceIn(-90, -20)

            // Speed correlates with signal strength
            val speedDropFactor = distToAp * 2.0 // 0.0 at ends, 1.0 in middle
            val dlBase = 150.0 - (speedDropFactor * 100.0)
            val ulBase = 50.0 - (speedDropFactor * 30.0)

            val dl = (dlBase + rng.nextGaussian() * 15.0).coerceAtLeast(5.0)
            val ul = (ulBase + rng.nextGaussian() * 8.0).coerceAtLeast(2.0)

            val powerMw = 10.0.pow(dbm.toDouble() / 10.0)

            val measurement = WifiMeasurement(
                timestamp = System.currentTimeMillis() + i * 60000L,
                building_pos = building,
                floor_lvl = floor,
                wifi_dbm = dbm,
                dl_mbps = dl,
                ul_mbps = ul,
                x_coord = x,
                y_coord = y
            )

            dao.insert(measurement)
            inserted++
        }

        return existingCount + inserted
    }

    /**
     * Populate ALL groups (5 buildings × 4 floors) with 30 points each.
     * Total: 600 measurements.
     */
    suspend fun populateAll(context: Context) {
        var total = 0
        for (building in ConfigViewModel.BUILDINGS) {
            for (floor in ConfigViewModel.FLOORS) {
                val count = populateGroup(context, building, floor, 30)
                total += 30
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Simulated $total measurements across 20 groups", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Populate corners for ALL groups (5 buildings × 4 floors) with a 20x20m rectangle.
     */
    suspend fun populateAllCorners(context: Context) {
        val cornerDao = (context.applicationContext as WifiSpatialApp).database.floorPlanCornerDao()
        cornerDao.deleteAll()
        
        var total = 0
        for (building in ConfigViewModel.BUILDINGS) {
            for (floor in ConfigViewModel.FLOORS) {
                // Generate realistic hallway corners for each building
                val (cornersList, _) = getHallwayPath(building)
                val corners = cornersList.mapIndexed { index, (x, y) ->
                    com.statproj.wifispatial.data.FloorPlanCorner(
                        building_pos = building,
                        floor_lvl = floor,
                        x_coord = x,
                        y_coord = y,
                        corner_order = index
                    )
                }
                cornerDao.insertAll(corners)
                total += cornersList.size
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Simulated corners across 20 groups", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Populate with data that has KNOWN statistical effects for validation.
     *
     * Design:
     * - Floor effect: each floor adds +5 dBm, +20 Mbps DL, +10 Mbps UL
     * - Building effect: each building adds +3 dBm, +10 Mbps DL, +5 Mbps UL
     * - Interaction: Floor 4 × South has an extra boost (tests interaction term)
     * - Within-cell noise: σ = 2.0 for dBm, σ = 5.0 for speeds
     *
     * This allows you to verify:
     * - ANOVA detects significant floor and building effects
     * - SS decomposition sums correctly
     * - Tukey HSD identifies correct pairwise differences
     * - Regression shows positive P_mW → speed correlation
     */
    suspend fun populateWithKnownEffects(context: Context) {
        val dao = (context.applicationContext as WifiSpatialApp).database.wifiMeasurementDao()
        dao.deleteAll()  // start fresh

        var id = 0
        for ((fi, floor) in ConfigViewModel.FLOORS.withIndex()) {
            for ((bi, building) in ConfigViewModel.BUILDINGS.withIndex()) {
                for (k in 0 until 30) {
                    // Place points along the hallway spine with some noise
                    val (_, spine) = getHallwayPath(building)
                    val fraction = k / 29.0
                    val (spineX, spineY) = getPointAlongSpine(spine, fraction)
                    val x = spineX + rng.nextGaussian() * 0.6 + bi * 50.0  // offset per building
                    val y = spineY + rng.nextGaussian() * 0.6 + fi * 50.0  // offset per floor

                    // Distance to nearest AP
                    val distToAp = minOf(fraction, 1.0 - fraction)
                    val signalDrop = distToAp * 2.0 * 30.0
                    val speedDropFactor = distToAp * 2.0

                    // Systematic effects
                    val floorEffect = fi * 5.0
                    val buildingEffect = bi * 3.0
                    val interactionEffect = if (fi == 3 && bi == 4) 10.0 else 0.0

                    // dBm
                    val dbm = (-35.0 - signalDrop + floorEffect + buildingEffect + interactionEffect
                            + rng.nextGaussian() * 2.0).toInt().coerceIn(-90, -20)

                    // Speeds with correlated effects
                    val dl = 150.0 - (speedDropFactor * 100.0) + fi * 20.0 + bi * 10.0 + interactionEffect * 2.0 +
                            rng.nextGaussian() * 5.0
                    val ul = 50.0 - (speedDropFactor * 30.0) + fi * 10.0 + bi * 5.0 + interactionEffect +
                            rng.nextGaussian() * 3.0

                    val powerMw = 10.0.pow(dbm.toDouble() / 10.0)

                    dao.insert(
                        WifiMeasurement(
                            timestamp = System.currentTimeMillis() + id * 1000L,
                            building_pos = building,
                            floor_lvl = floor,
                            wifi_dbm = dbm,
                            dl_mbps = dl.coerceAtLeast(1.0),
                            ul_mbps = ul.coerceAtLeast(1.0),
                            x_coord = x,
                            y_coord = y
                        )
                    )
                    id++
                }
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Generated $id measurements with known effects\n" +
                "Floor effect: +5 dBm/floor\n" +
                "Building effect: +3 dBm/building\n" +
                "Interaction: Floor 4 × South",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Generate data that tests proximity edge cases.
     *
     * Creates clusters of points at various distances to verify the 3m rule:
     * - Some points at exactly 3m (should be rejected)
     * - Some at 2.9m (should be rejected)
     * - Some at 3.1m (should be accepted)
     * - Some at 10m+ (clearly accepted)
     */
    suspend fun populateProximityTestData(context: Context) {
        val dao = (context.applicationContext as WifiSpatialApp).database.wifiMeasurementDao()
        dao.deleteAll()

        val building = "North"
        val floor = "Floor 1"

        // Point 1: origin
        dao.insert(makeMeasurement(building, floor, 0.0, 0.0))

        // Point 2: exactly 3m east — should have been rejected (but we insert for testing the check logic)
        dao.insert(makeMeasurement(building, floor, 3.0, 0.0))

        // Point 3: 3.5m north of origin — valid
        dao.insert(makeMeasurement(building, floor, 0.0, 3.5))

        // Point 4: 5m diagonal — valid
        dao.insert(makeMeasurement(building, floor, 3.536, 3.536))

        // Point 5: 10m east — valid
        dao.insert(makeMeasurement(building, floor, 10.0, 0.0))

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Generated 5 proximity test points", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeMeasurement(
        building: String,
        floor: String,
        x: Double,
        y: Double
    ): WifiMeasurement {
        val dbm = -50 + rng.nextInt(20) - 10
        return WifiMeasurement(
            timestamp = System.currentTimeMillis(),
            building_pos = building,
            floor_lvl = floor,
            wifi_dbm = dbm,
            dl_mbps = 100.0 + rng.nextDouble() * 50.0,
            ul_mbps = 40.0 + rng.nextDouble() * 20.0,
            x_coord = x,
            y_coord = y
        )
    }
}
