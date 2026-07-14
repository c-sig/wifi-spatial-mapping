package com.statproj.wifispatial.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.data.WifiMeasurementDao
import com.statproj.wifispatial.network.SpeedTestEngine
import com.statproj.wifispatial.sensor.CardinalDirection
import com.statproj.wifispatial.sensor.SharedSensorManager
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.data.CoordinatePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Directions for the D-Pad navigation.
 */
enum class DPadDirection {
    FORWARD, BACKWARD, LEFT, RIGHT
}

/**
 * States the collection process can be in.
 */
enum class CollectionPhase {
    /** Idle, waiting for user to start */
    IDLE,
    /** User is moving to the next spot via D-Pad */
    WALKING,
    /** Proximity check triggered — too close to existing point */
    TOO_CLOSE,
    /** Distance OK, speed test about to run */
    READY_TO_TEST,
    /** Running download speed test */
    TESTING_DOWNLOAD,
    /** Running upload speed test */
    TESTING_UPLOAD,
    /** Test complete, waiting for user to resume */
    TEST_COMPLETE,
    /** Test failed */
    TEST_FAILED,
    /** Maximum samples reached for this group */
    MAX_REACHED
}

/**
 * UI state for the collection screen.
 */
data class CollectionUiState(
    val phase: CollectionPhase = CollectionPhase.IDLE,
    val building: String = "",
    val floor: String = "",
    val currentCount: Int = 0,
    val maxSamples: Int = ConfigViewModel.PRESCRIBED_SAMPLE_SIZE,
    val lastDlMbps: Double? = null,
    val lastUlMbps: Double? = null,
    val lastDbm: Int? = null,
    val measurements: List<CoordinatePoint> = emptyList(),
    val statusMessage: String = "Ready to collect",
    val corners: List<FloorPlanCorner> = emptyList(),
    // Arm's Reach Grid specific states:
    val currentX: Int = 0,
    val currentY: Int = 0
)

/**
 * ViewModel for the data collection screen.
 * Orchestrates PDR tracking, proximity checks, Wi-Fi measurements,
 * and Cloudflare speed tests.
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: WifiMeasurementDao =
        (application as WifiSpatialApp).database.wifiMeasurementDao()

    private val cornerDao = (application as WifiSpatialApp).database.floorPlanCornerDao()

    private val speedTestEngine = SpeedTestEngine

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    /**
     * Checks if there's a handover from ConfigScreen and auto-starts collection.
     */
    fun checkAutoStartHandover(context: Context) {
        if (SharedSensorManager.isHandoverToCollect) {
            SharedSensorManager.isHandoverToCollect = false
            startCollection(
                context = context,
                building = SharedSensorManager.handoverBuilding,
                floor = SharedSensorManager.handoverFloor
            )
        }
    }

    // ── Collection Control ──────────────────────────────────────────────

    /**
     * Start data collection session for a given building/floor group.
     * Enters the SETUP_CORNER phase.
     */
    fun startCollection(context: Context, building: String, floor: String) {
        viewModelScope.launch {
            val count = dao.countByGroup(building, floor)

            val initialMeasurements = dao.getCoordinatesByGroup(building, floor)

            _uiState.value = _uiState.value.copy(
                phase = CollectionPhase.WALKING,
                building = building,
                floor = floor,
                currentCount = count,
                measurements = initialMeasurements,
                currentX = 0,
                currentY = 0,
                statusMessage = "Started! Move using the D-Pad."
            )
        }
    }



    /**
     * Stop collection session.
     */
    fun stopCollection() {
        _uiState.value = _uiState.value.copy(phase = CollectionPhase.IDLE)
    }

    /**
     * Undo the most recently collected measurement.
     * Deletes the point, steps the avatar back to the previous coordinate, and returns to WALKING.
     */
    fun undoLastMeasurement() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.measurements.isEmpty()) return@launch

            dao.deleteLastMeasurementByGroup(state.building, state.floor)

            // Refresh data
            val newCount = dao.countByGroup(state.building, state.floor)
            val updatedMeasurements = dao.getCoordinatesByGroup(state.building, state.floor)

            // Re-calculate boundaries
            recalculateCorners(state.building, state.floor)

            // Set current X and Y to the last remaining point, or 0,0 if empty
            val prevX = updatedMeasurements.lastOrNull()?.x_coord?.toInt() ?: 0
            val prevY = updatedMeasurements.lastOrNull()?.y_coord?.toInt() ?: 0

            _uiState.value = state.copy(
                currentCount = newCount,
                measurements = updatedMeasurements,
                currentX = prevX,
                currentY = prevY,
                statusMessage = "Point deleted. Stepped back.",
                phase = CollectionPhase.WALKING
            )
        }
    }

    /**
     * Resume walking after a test point is saved.
     */
    fun resumeWalking() {
        if (_uiState.value.phase == CollectionPhase.TEST_COMPLETE ||
            _uiState.value.phase == CollectionPhase.TEST_FAILED ||
            _uiState.value.phase == CollectionPhase.TOO_CLOSE
        ) {
            _uiState.value = _uiState.value.copy(
                phase = CollectionPhase.WALKING,
                statusMessage = "Ready. Use D-Pad to move."
            )
        }
    }

    /**
     * D-Pad Step Handler. Updates position based on direction faced and triggers a test.
     */
    fun step(context: Context, stepDirection: DPadDirection) {
        if (_uiState.value.phase != CollectionPhase.WALKING &&
            _uiState.value.phase != CollectionPhase.TOO_CLOSE) {
            return
        }

        val state = _uiState.value
        var dx = 0
        var dy = 0

        when (stepDirection) {
            DPadDirection.FORWARD -> dy = 1
            DPadDirection.BACKWARD -> dy = -1
            DPadDirection.LEFT -> dx = -1
            DPadDirection.RIGHT -> dx = 1
        }

        val newX = state.currentX + dx
        val newY = state.currentY + dy

        _uiState.value = state.copy(
            currentX = newX,
            currentY = newY
        )

        viewModelScope.launch {
            performProximityCheck(context, newX, newY)
        }
    }

    // ── Proximity Check ─────────────────────────────────────────────────

    private suspend fun performProximityCheck(context: Context, currentX: Int, currentY: Int) {
        val state = _uiState.value
        val previousPoints = dao.getCoordinatesByGroup(state.building, state.floor)

        // Exact match check on the integer grid
        val isAlreadyMeasured = previousPoints.any { it.x_coord.toInt() == currentX && it.y_coord.toInt() == currentY }

        if (!isAlreadyMeasured) {
            // Grid cell is empty — run speed test
            _uiState.value = state.copy(
                phase = CollectionPhase.READY_TO_TEST,
                statusMessage = "Running speed test at ($currentX, $currentY)..."
            )
            performSpeedTest(context, currentX.toDouble(), currentY.toDouble())
        } else {
            // Already measured
            _uiState.value = state.copy(
                phase = CollectionPhase.TOO_CLOSE,
                statusMessage = "Point ($currentX, $currentY) already measured! Move somewhere else."
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Point already measured!", Toast.LENGTH_SHORT).show()
            }
            resumeWalking()
        }
    }

    // ── Speed Test & Data Save ──────────────────────────────────────────

    /**
     * Execute sequential download + upload speed tests,
     * then save the measurement to Room.
     */
    private suspend fun performSpeedTest(context: Context, x: Double, y: Double) {
        val state = _uiState.value

        // Check Wi-Fi frequency — must be 5GHz
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val frequency = wifiInfo.frequency
        val rssi = wifiInfo.rssi

        if (frequency < 5000) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Only 5GHz data allowed.", Toast.LENGTH_LONG).show()
            }
            _uiState.value = state.copy(
                phase = CollectionPhase.WALKING,
                statusMessage = "Discarded: not 5GHz ($frequency MHz). Tap again."
            )
            resumeWalking()
            return
        }

        try {
            // Download test
            _uiState.value = _uiState.value.copy(
                phase = CollectionPhase.TESTING_DOWNLOAD,
                statusMessage = "Running download test...",
                lastDbm = rssi
            )

            val dlResult = speedTestEngine.runDownloadTest()

            // Upload test
            _uiState.value = _uiState.value.copy(
                phase = CollectionPhase.TESTING_UPLOAD,
                statusMessage = "Running upload test...",
                lastDlMbps = dlResult
            )

            val ulResult = speedTestEngine.runUploadTest()

            // Check if both tests succeeded
            if (dlResult == null && ulResult == null) {
                throw Exception("Both speed tests failed")
            }

            // Save to Room
            val measurement = WifiMeasurement(
                timestamp = System.currentTimeMillis(),
                building_pos = state.building,
                floor_lvl = state.floor,
                wifi_dbm = rssi,
                dl_mbps = dlResult,
                ul_mbps = ulResult,
                x_coord = x,
                y_coord = y
            )

            dao.insert(measurement)
            
            // Recalculate grid boundary corners dynamically
            recalculateCorners(state.building, state.floor)

            val newCount = state.currentCount + 1
            _uiState.value = _uiState.value.copy(
                phase = CollectionPhase.WALKING,
                currentCount = newCount,
                lastDlMbps = dlResult,
                lastUlMbps = ulResult,
                lastDbm = rssi,
                statusMessage = if (newCount >= ConfigViewModel.PRESCRIBED_SAMPLE_SIZE)
                    "Point $newCount saved. You can keep collecting."
                else
                    "Point $newCount/30 saved. Tap next spot."
            )

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                phase = CollectionPhase.TEST_FAILED,
                statusMessage = "Test Failed — Retrying"
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Test Failed — Retrying", Toast.LENGTH_SHORT).show()
            }
            resumeWalking()
        }
    }

    private suspend fun recalculateCorners(building: String, floor: String) {
        val allPoints = dao.getCoordinatesByGroup(building, floor)
        if (allPoints.isEmpty()) return
        
        // 1. Gather all directed edges for 1x1 cells centered on each point
        data class Point(val x: Double, val y: Double)
        data class Edge(val p1: Point, val p2: Point)
        
        val edges = mutableListOf<Edge>()
        val padding = 0.5
        
        for (pt in allPoints) {
            val nw = Point(pt.x_coord - padding, pt.y_coord - padding)
            val ne = Point(pt.x_coord + padding, pt.y_coord - padding)
            val se = Point(pt.x_coord + padding, pt.y_coord + padding)
            val sw = Point(pt.x_coord - padding, pt.y_coord + padding)
            
            edges.add(Edge(nw, ne)) // Top
            edges.add(Edge(ne, se)) // Right
            edges.add(Edge(se, sw)) // Bottom
            edges.add(Edge(sw, nw)) // Left
        }
        
        // 2. Cancel out internal edges (if an edge has a reverse edge, both are internal)
        val edgeCounts = mutableMapOf<Edge, Int>()
        for (e in edges) {
            edgeCounts[e] = edgeCounts.getOrDefault(e, 0) + 1
        }
        
        val outerEdges = edges.filter { e ->
            val reverseEdge = Edge(e.p2, e.p1)
            !edgeCounts.containsKey(reverseEdge)
        }.toMutableList()
        
        if (outerEdges.isEmpty()) return
        
        // 3. Chain the edges together to form a polygon
        val polygon = mutableListOf<Point>()
        var currentEdge = outerEdges.first()
        outerEdges.remove(currentEdge)
        polygon.add(currentEdge.p1)
        
        while (outerEdges.isNotEmpty()) {
            val nextEdge = outerEdges.find { it.p1 == currentEdge.p2 }
            if (nextEdge != null) {
                polygon.add(nextEdge.p1)
                currentEdge = nextEdge
                outerEdges.remove(nextEdge)
            } else {
                // Should not happen with contiguous squares, but break if it does
                break
            }
        }
        
        // 4. Simplify: Remove collinear points
        val simplifiedPolygon = mutableListOf<Point>()
        if (polygon.size > 2) {
            for (i in polygon.indices) {
                val prev = if (i == 0) polygon.last() else polygon[i - 1]
                val curr = polygon[i]
                val next = if (i == polygon.size - 1) polygon.first() else polygon[i + 1]
                
                // Cross product to check collinearity
                val crossProduct = (curr.x - prev.x) * (next.y - curr.y) - (curr.y - prev.y) * (next.x - curr.x)
                if (Math.abs(crossProduct) > 0.0001) {
                    simplifiedPolygon.add(curr)
                }
            }
        } else {
            simplifiedPolygon.addAll(polygon)
        }
        
        // Clear old corners
        cornerDao.deleteByGroup(building, floor)

        val bounds = simplifiedPolygon.mapIndexed { index, pt ->
            FloorPlanCorner(
                building_pos = building, 
                floor_lvl = floor, 
                x_coord = pt.x, 
                y_coord = pt.y, 
                corner_order = index + 1
            )
        }
        
        bounds.forEach { cornerDao.insert(it) }
        
        _uiState.value = _uiState.value.copy(
            corners = bounds,
            measurements = allPoints
        )
    }

    override fun onCleared() {
        super.onCleared()
    }
}
