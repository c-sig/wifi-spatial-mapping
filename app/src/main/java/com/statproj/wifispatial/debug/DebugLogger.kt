package com.statproj.wifispatial.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton logger that holds an in-memory ring buffer of logs
 * to be displayed in the Debug terminal tab.
 */
object DebugLogger {
    private const val MAX_LOGS = 500
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedMsg = "[$timestamp] $message"
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(formattedMsg)
        
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeAt(0)
        }
        
        _logs.value = currentLogs
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
