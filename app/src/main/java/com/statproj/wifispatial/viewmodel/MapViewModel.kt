package com.statproj.wifispatial.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.data.FloorPlanCornerDao
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.data.WifiMeasurementDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Map / Spatial Heatmap screen.
 *
 * Manages building/floor selection and loads the corresponding
 * floor-plan corners and Wi-Fi measurements from the database.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val cornerDao: FloorPlanCornerDao =
        (application as WifiSpatialApp).database.floorPlanCornerDao()

    private val measurementDao: WifiMeasurementDao =
        (application as WifiSpatialApp).database.wifiMeasurementDao()

    // ── Selection state ─────────────────────────────────────────────

    private val _selectedBuilding = MutableStateFlow(ConfigViewModel.BUILDINGS[0])
    val selectedBuilding: StateFlow<String> = _selectedBuilding.asStateFlow()

    private val _selectedFloor = MutableStateFlow(ConfigViewModel.FLOORS[0])
    val selectedFloor: StateFlow<String> = _selectedFloor.asStateFlow()

    // ── Data state ──────────────────────────────────────────────────

    private val _corners = MutableStateFlow<List<FloorPlanCorner>>(emptyList())
    val corners: StateFlow<List<FloorPlanCorner>> = _corners.asStateFlow()

    private val _measurements = MutableStateFlow<List<WifiMeasurement>>(emptyList())
    val measurements: StateFlow<List<WifiMeasurement>> = _measurements.asStateFlow()

    init {
        loadData()
    }

    // ── Actions ─────────────────────────────────────────────────────

    fun selectBuilding(building: String) {
        _selectedBuilding.value = building
        loadData()
    }

    fun selectFloor(floor: String) {
        _selectedFloor.value = floor
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val building = _selectedBuilding.value
            val floor = _selectedFloor.value
            _corners.value = cornerDao.getByGroup(building, floor)
            _measurements.value = measurementDao.getByGroup(building, floor)
        }
    }
}
