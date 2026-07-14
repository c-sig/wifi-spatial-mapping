package com.statproj.wifispatial.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CardinalDirection {
    NORTH, SOUTH, EAST, WEST
}

class CompassTracker : SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    
    private var isTracking = false

    private val _direction = MutableStateFlow(CardinalDirection.NORTH)
    val direction: StateFlow<CardinalDirection> = _direction.asStateFlow()

    fun start(context: Context) {
        if (isTracking) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isTracking = true
        }
    }

    fun stop() {
        if (!isTracking) return
        sensorManager?.unregisterListener(this)
        isTracking = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)

            // Azimuth in radians
            val azimuthRad = orientationValues[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0) {
                azimuthDeg += 360f
            }

            // Snap to cardinal direction
            val snappedDirection = when {
                azimuthDeg >= 315 || azimuthDeg < 45 -> CardinalDirection.NORTH
                azimuthDeg in 45f..135f -> CardinalDirection.EAST
                azimuthDeg > 135f && azimuthDeg < 225f -> CardinalDirection.SOUTH
                else -> CardinalDirection.WEST
            }
            
            _direction.value = snappedDirection
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
