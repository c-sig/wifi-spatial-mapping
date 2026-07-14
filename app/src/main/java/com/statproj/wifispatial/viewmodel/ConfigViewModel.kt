package com.statproj.wifispatial.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.data.FloorPlanCornerDao
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.data.WifiMeasurementDao
import com.statproj.wifispatial.network.SpeedTestServer
import com.statproj.wifispatial.sensor.SharedSensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Manually select building/floor and proceed to Collection.
 */
class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: WifiMeasurementDao =
        (application as WifiSpatialApp).database.wifiMeasurementDao()


    companion object {
        const val PRESCRIBED_SAMPLE_SIZE = 30

        val BUILDINGS = listOf("North", "North West", "West", "South West", "South")
        val FLOORS = listOf("Floor 1", "Floor 2", "Floor 3", "Floor 4")
    }

    // ── State ───────────────────────────────────────────────────────────

    private val _selectedBuilding = MutableStateFlow(BUILDINGS[0])
    val selectedBuilding: StateFlow<String> = _selectedBuilding.asStateFlow()

    private val _selectedFloor = MutableStateFlow(FLOORS[0])
    val selectedFloor: StateFlow<String> = _selectedFloor.asStateFlow()

    private val _currentCount = MutableStateFlow(0)
    val currentCount: StateFlow<Int> = _currentCount.asStateFlow()

    private val _maxReached = MutableStateFlow(false)
    val maxReached: StateFlow<Boolean> = _maxReached.asStateFlow()

    private val _selectedServer = MutableStateFlow(SpeedTestServer.LOCAL)
    val selectedServer: StateFlow<SpeedTestServer> = _selectedServer.asStateFlow()
    // ── Navigation Events ───────────────────────────────────────────────────

    private val _navigateToCollect = Channel<Unit>(Channel.BUFFERED)
    val navigateToCollect = _navigateToCollect.receiveAsFlow()

    init {
        refreshCount()
    }

    // ── Actions ─────────────────────────────────────────────────────────

    fun selectBuilding(building: String) {
        _selectedBuilding.value = building
        refreshCount()
    }

    fun selectFloor(floor: String) {
        _selectedFloor.value = floor
        refreshCount()
    }

    fun setSpeedTestServer(server: SpeedTestServer) {
        _selectedServer.value = server
        com.statproj.wifispatial.network.SpeedTestEngine.selectedServer = server
    }

    fun refreshCount() {
        viewModelScope.launch {
            val count = dao.countByGroup(
                _selectedBuilding.value,
                _selectedFloor.value
            )
            _currentCount.value = count
            _maxReached.value = count >= PRESCRIBED_SAMPLE_SIZE
        }
    }
    fun proceedToCollection() {
        val building = _selectedBuilding.value
        val floor = _selectedFloor.value
        
        SharedSensorManager.isHandoverToCollect = true
        SharedSensorManager.handoverBuilding = building
        SharedSensorManager.handoverFloor = floor
        
        viewModelScope.launch {
            _navigateToCollect.send(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
