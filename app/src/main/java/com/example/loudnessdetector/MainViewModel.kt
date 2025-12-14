package com.example.loudnessdetector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.loudnessdetector.data.AlertMessage
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel : ViewModel() {
    
    private val gson = Gson()
    
    var isConnected by mutableStateOf(false)
        private set
    
    var deviceName by mutableStateOf("Loading...")
        private set
    
    var deviceStatus by mutableStateOf("Disconnected")
        private set
    
    var lastRms by mutableStateOf(0)
        private set
    
    var lastZcr by mutableStateOf(0.0)
        private set
    
    var calibrationProgress by mutableStateOf(0)
        private set
    
    var isCalibrating by mutableStateOf(false)
        private set
    
    val alerts = mutableStateListOf<AlertItem>()
    
    fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        deviceStatus = if (connected) "Connected" else "Disconnected"
    }
    
    fun handleAlertMessage(json: String) {
        try {
            val alert = gson.fromJson(json, AlertMessage::class.java)
            
            lastRms = alert.rms
            lastZcr = alert.zcr
            
            if (alert.event == "SPEECH") {
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val timestamp = timeFormat.format(Date(alert.timestamp * 1000))
                
                alerts.add(0, AlertItem(
                    time = timestamp,
                    message = "Speech detected!",
                    rms = alert.rms,
                    zcr = String.format("%.3f", alert.zcr),
                    confidence = alert.confidence
                ))
                
                // Keep only last 20 alerts
                if (alerts.size > 20) {
                    alerts.removeAt(alerts.size - 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun handleStatusMessage(json: String) {
        try {
            val jsonObj = gson.fromJson(json, Map::class.java) as Map<*, *>
            val type = jsonObj["type"] as? String
            
            when (type) {
                "device_info" -> {
                    deviceName = jsonObj["device_name"] as? String ?: "Unknown Device"
                    deviceStatus = "Online"
                }
                "calibration_progress" -> {
                    isCalibrating = true
                    val progress = jsonObj["progress"] as? Map<*, *>
                    calibrationProgress = (progress?.get("percentage") as? Double)?.toInt() ?: 0
                }
                "calibration_complete" -> {
                    isCalibrating = false
                    calibrationProgress = 0
                    val success = jsonObj["calibration_complete"] as? Boolean ?: false
                    deviceStatus = if (success) "Calibration Success!" else "Calibration Failed"
                }
                "status" -> {
                    deviceStatus = "Online"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearAlerts() {
        alerts.clear()
    }
}

data class AlertItem(
    val time: String,
    val message: String,
    val rms: Int,
    val zcr: String,
    val confidence: String
)
